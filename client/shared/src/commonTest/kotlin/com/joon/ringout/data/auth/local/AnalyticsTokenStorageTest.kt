package com.joon.ringout.data.auth.local

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.auth.removeAuthTokens
import com.joon.ringout.data.auth.replaceAuthTokens
import com.joon.ringout.data.auth.restoreAuthSession
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnalyticsTokenStorageTest {
    @Test
    fun `앱 시작에서 저장된 회원 ID를 복원하고 토큰이 없으면 이전 ID를 해제한다`() = runTest {
        for (initial in listOf(tokens(42), null)) {
            val fixture = Fixture(initial)
            fixture.storage.synchronizeUserId()
            fixture.storage.restoreAuthSession(fixture.session)

            assertEquals(List<String?>(2) { initial?.let { "42" } }, fixture.ids)
            assertEquals(
                if (initial == null) AuthSessionState.Unauthenticated else AuthSessionState.Authenticated,
                fixture.session.state.value,
            )
        }
    }

    @Test
    fun `시작 시 저장소 조회 실패는 앱 시작을 막지 않고 이전 ID를 해제한다`() {
        val ids = mutableListOf<String?>()
        val storage = AnalyticsTokenStorage(
            delegate = MemoryTokens(),
            tokenSnapshot = { error("Secure storage unavailable") },
            setUserId = { ids += it },
        )

        storage.synchronizeUserId()

        assertEquals(listOf<String?>(null), ids)
    }

    @Test
    fun `시작 시 토큰을 읽지 못해도 이후 세션 복원에 성공하면 회원 ID를 복구한다`() = runTest {
        val delegate = MemoryTokens(tokens(42))
        val session = AuthSession()
        val ids = mutableListOf<String?>()
        val storage = AnalyticsTokenStorage(
            delegate = delegate,
            tokenSnapshot = { error("Secure storage is locked") },
            setUserId = {
                assertEquals(AuthSessionState.Restoring, session.state.value)
                ids += it
            },
        )
        storage.synchronizeUserId()
        assertEquals(listOf<String?>(null), ids)

        storage.restoreAuthSession(session)

        assertEquals(listOf<String?>(null, "42"), ids)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
    }

    @Test
    fun `로그인과 계정 전환은 새 세션을 공개하기 전에 저장된 회원 ID를 반영한다`() = runTest {
        val fixture = Fixture()
        fixture.storage.replaceAuthTokens(tokens(1), fixture.session)
        val firstIdentity = fixture.session.identity.value
        fixture.storage.replaceAuthTokens(tokens(2), fixture.session)

        assertEquals(listOf<String?>("1", "2"), fixture.ids)
        assertEquals(listOf<Any?>(null, firstIdentity), fixture.identitiesAtUpdate)
        assertEquals(listOf<AuthTokens?>(tokens(1), tokens(2)), fixture.tokensAtUpdate)
        assertTrue(fixture.session.identity.value !== firstIdentity)
        assertEquals(AuthSessionState.Authenticated, fixture.session.state.value)
    }

    @Test
    fun `로그아웃은 저장된 토큰과 Analytics ID를 함께 해제한다`() = runTest {
        val fixture = Fixture()
        fixture.storage.replaceAuthTokens(tokens(1), fixture.session)
        fixture.storage.removeAuthTokens(fixture.session)

        assertEquals(listOf<String?>("1", null), fixture.ids)
        assertNull(fixture.delegate.tokens)
        assertEquals(AuthSessionState.Unauthenticated, fixture.session.state.value)
    }

    @Test
    fun `인증 만료 응답은 Analytics ID를 해제하고 재인증 상태를 보존한다`() = runTest {
        val fixture = Fixture()
        fixture.storage.replaceAuthTokens(tokens(1), fixture.session)
        val client = HttpClient(MockEngine {
            respond(Unauthorized, HttpStatusCode.Unauthorized, JsonHeaders)
        }) { configureRingoutHttpClient() }
        try {
            assertFailsWith<ApiException> {
                AuthenticatedRequestExecutor(client, fixture.storage, fixture.session).execute { token ->
                    client.get("https://example.com/protected") { bearerAuth(token) }
                }
            }
            assertEquals(listOf<String?>("1", null), fixture.ids)
            assertTrue(fixture.storage.isReauthenticationRequired())
            assertEquals(AuthSessionState.ReauthenticationRequired, fixture.session.state.value)
        } finally {
            client.close()
        }
    }

    @Test
    fun `토큰 저장과 삭제가 실패하면 현재 Analytics ID를 변경하지 않는다`() = runTest {
        val fixture = Fixture()
        fixture.storage.replaceAuthTokens(tokens(1), fixture.session)
        fixture.delegate.failMutation = true

        assertFailsWith<IllegalStateException> { fixture.storage.replaceAuthTokens(tokens(2), fixture.session) }
        assertFailsWith<IllegalStateException> { fixture.storage.removeAuthTokens(fixture.session) }
        assertFailsWith<IllegalStateException> { fixture.storage.expire() }

        assertEquals(listOf<String?>("1"), fixture.ids)
        assertEquals(tokens(1), fixture.delegate.tokens)
        assertEquals(AuthSessionState.Authenticated, fixture.session.state.value)
    }

    @Test
    fun `잘못된 토큰으로 교체하면 이전 계정 ID를 남기지 않는다`() = runTest {
        val fixture = Fixture()
        fixture.storage.replaceAuthTokens(tokens(1), fixture.session)
        fixture.storage.replaceAuthTokens(AuthTokens("invalid-token", "refresh"), fixture.session)

        assertEquals(listOf<String?>("1", null), fixture.ids)
    }

    @Test
    fun `같은 회원의 토큰 갱신은 동일한 ID를 유지한다`() = runTest {
        val fixture = Fixture()
        fixture.storage.replaceAuthTokens(tokens(1), fixture.session)
        val identity = fixture.session.identity.value
        val renewed = tokens(1, "renewed")
        var protectedRequests = 0
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/reissue")) {
                respond(reissue(renewed), headers = JsonHeaders)
            } else if (protectedRequests++ == 0) {
                respond(Expired, HttpStatusCode.Unauthorized, JsonHeaders)
            } else {
                respond("{}", headers = JsonHeaders)
            }
        }) { configureRingoutHttpClient() }
        try {
            AuthenticatedRequestExecutor(client, fixture.storage, fixture.session).execute { token ->
                client.get("https://example.com/protected") { bearerAuth(token) }
            }
            assertEquals(listOf<String?>("1", "1"), fixture.ids)
            assertEquals(renewed, fixture.delegate.tokens)
            assertTrue(fixture.session.identity.value === identity)
        } finally {
            client.close()
        }
    }

    @Test
    fun `이전 계정의 늦은 갱신 응답은 새 계정 ID를 덮어쓰지 않는다`() = runTest {
        val fixture = Fixture()
        fixture.storage.replaceAuthTokens(tokens(1), fixture.session)
        val refreshStarted = CompletableDeferred<Unit>()
        val allowRefresh = CompletableDeferred<Unit>()
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("/reissue")) {
                refreshStarted.complete(Unit)
                allowRefresh.await()
                respond(reissue(tokens(1, "renewed")), headers = JsonHeaders)
            } else {
                respond(Expired, HttpStatusCode.Unauthorized, JsonHeaders)
            }
        }) { configureRingoutHttpClient() }
        try {
            val pending = async {
                assertFailsWith<ApiException> {
                    AuthenticatedRequestExecutor(client, fixture.storage, fixture.session).execute { token ->
                        client.get("https://example.com/protected") { bearerAuth(token) }
                    }
                }
            }
            refreshStarted.await()
            fixture.storage.replaceAuthTokens(tokens(2), fixture.session)
            allowRefresh.complete(Unit)
            pending.await()

            assertEquals(listOf<String?>("1", "2"), fixture.ids)
            assertEquals(tokens(2), fixture.delegate.tokens)
        } finally {
            allowRefresh.complete(Unit)
            client.close()
        }
    }

    @Test
    fun `토큰 조회와 스냅샷 조회는 Analytics ID를 변경하지 않는다`() = runTest {
        val fixture = Fixture(tokens(1))
        assertEquals(tokens(1), fixture.storage.read())
        assertEquals(tokens(1), fixture.storage.readSnapshot())
        assertTrue(fixture.ids.isEmpty())
    }

    @Test
    fun `Analytics SDK 실패는 로그인과 로그아웃 성공을 막지 않는다`() = runTest {
        val delegate = MemoryTokens()
        val storage = AnalyticsTokenStorage(delegate, { delegate.tokens }) { error("SDK unavailable") }
        val session = AuthSession()
        storage.synchronizeUserId()
        storage.replaceAuthTokens(tokens(1), session)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        storage.removeAuthTokens(session)
        assertEquals(AuthSessionState.Unauthenticated, session.state.value)
        assertNull(delegate.tokens)
    }
}

private class Fixture(initial: AuthTokens? = null) {
    val delegate = MemoryTokens(initial)
    val session = AuthSession()
    val ids = mutableListOf<String?>()
    val tokensAtUpdate = mutableListOf<AuthTokens?>()
    val identitiesAtUpdate = mutableListOf<Any?>()
    val storage = AnalyticsTokenStorage(delegate, { delegate.tokens }) { id ->
        ids += id
        tokensAtUpdate += delegate.tokens
        identitiesAtUpdate += session.identity.value
    }
}

private class MemoryTokens(var tokens: AuthTokens? = null) : SecureTokenStorage {
    var failMutation = false
    private var reauthenticationRequired = false

    override suspend fun save(tokens: AuthTokens) {
        check(!failMutation)
        this.tokens = tokens
        reauthenticationRequired = false
    }

    override suspend fun read(): AuthTokens? = tokens

    override suspend fun clear() {
        check(!failMutation)
        tokens = null
        reauthenticationRequired = false
    }

    override suspend fun expire() {
        check(!failMutation)
        tokens = null
        reauthenticationRequired = true
    }

    override suspend fun isReauthenticationRequired(): Boolean = reauthenticationRequired
}

private fun tokens(id: Int, signature: String = "signature"): AuthTokens {
    val payload = Base64.UrlSafe.encode("""{"sub":"$id","userId":$id,"tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=')
    return AuthTokens("header.$payload.$signature", "refresh-$id")
}

private fun reissue(tokens: AuthTokens): String =
    """{"isSuccess":true,"code":"AUTH200","message":"OK","result":{"accessToken":"${tokens.accessToken}","refreshToken":"${tokens.refreshToken}"}}"""

private val JsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private const val Expired = """{"isSuccess":false,"code":"AUTH401","message":"expired"}"""
private const val Unauthorized = """{"isSuccess":false,"code":"COMMON401","message":"unauthorized"}"""
