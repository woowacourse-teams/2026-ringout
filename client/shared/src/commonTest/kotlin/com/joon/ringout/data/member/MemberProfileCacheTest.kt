package com.joon.ringout.data.member

import com.joon.ringout.data.auth.removeAuthTokens
import com.joon.ringout.data.auth.replaceAuthTokens
import com.joon.ringout.data.auth.restoreAuthSession
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class MemberProfileCacheTest {
    @Test
    fun `닉네임 수정과 탈퇴 실패는 기존 캐시를 변경하지 않는다`() = runTest {
        val fixture = ProfileFixture(this)
        val original = fixture.repository.getProfile()
        fixture.failMutation = true
        assertFailsWith<IllegalStateException> { fixture.repository.updateNickname("실패") }
        assertEquals(original, fixture.repository.getCachedProfile())
        assertFailsWith<IllegalStateException> { fixture.repository.withdraw() }
        assertEquals(original, fixture.repository.getCachedProfile())
        fixture.client.close()
    }

    @Test
    fun `닉네임 수정 중 액세스 토큰이 재발급되어도 캐시와 로그인 세션을 유지한다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        val identity = fixture.session.identity.value
        fixture.expireNextPatch = true
        fixture.repository.updateNickname("수정")
        assertSame(identity, fixture.session.identity.value)
        assertEquals("서버닉네임", fixture.repository.getProfile().nickname)
        assertEquals(1, fixture.getCount)
        assertEquals(1, fixture.reissueCount)
        fixture.client.close()
    }

    @Test
    fun `닉네임 수정 응답 전에 계정이 바뀌면 새 계정의 캐시를 수정하지 않는다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        val gate = CompletableDeferred<Unit>()
        fixture.beforePatch = { gate.await() }
        val pending = async { fixture.repository.updateNickname("이전계정수정") }
        runCurrent()
        fixture.session.startNewSession()
        fixture.nickname = "새계정"
        fixture.repository.getProfile()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals("새계정", fixture.repository.getCachedProfile()?.nickname)
        fixture.client.close()
    }

    @Test
    fun `탈퇴 전에 시작한 조회는 탈퇴 후 캐시를 다시 채우지 않는다`() = runTest {
        val fixture = ProfileFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeGet = { withContext(NonCancellable) { gate.await() } }
        val pending = async { fixture.repository.getProfile() }
        runCurrent()
        fixture.repository.withdraw()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertNull(fixture.repository.getCachedProfile())
        fixture.client.close()
    }

    @Test
    fun `같은 세션의 반복 조회는 서버를 한 번만 호출한다`() = runTest {
        val fixture = ProfileFixture(this)
        repeat(3) { assertEquals("원래닉네임", fixture.repository.getProfile().nickname) }
        assertEquals(1, fixture.getCount)
        assertEquals("원래닉네임", fixture.repository.getCachedProfile()?.nickname)
        fixture.client.close()
    }

    @Test
    fun `동시 조회는 진행 중인 요청을 공유하고 한 호출자의 취소는 다른 조회를 취소하지 않는다`() = runTest {
        val fixture = ProfileFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeGet = { gate.await() }
        val first = async { fixture.repository.getProfile() }
        val second = async { fixture.repository.getProfile() }
        runCurrent()
        first.cancel()
        gate.complete(Unit)
        assertEquals("원래닉네임", second.await().nickname)
        assertEquals(1, fixture.getCount)
        fixture.client.close()
    }

    @Test
    fun `로그아웃과 재로그인 후에는 이전 프로필을 재사용하지 않는다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        fixture.tokens.removeAuthTokens(fixture.session)
        assertNull(fixture.repository.getCachedProfile())
        fixture.nickname = "다른계정"
        fixture.tokens.replaceAuthTokens(AuthTokens("other", "other-refresh"), fixture.session)
        assertNull(fixture.repository.getCachedProfile())
        assertEquals("다른계정", fixture.repository.getProfile().nickname)
        assertEquals(2, fixture.getCount)
        fixture.client.close()
    }

    @Test
    fun `인증 상태가 유지되어도 새 로그인은 캐시를 무효화한다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        fixture.tokens.replaceAuthTokens(AuthTokens("other", "other-refresh"), fixture.session)
        assertNull(fixture.repository.getCachedProfile())
        fixture.nickname = "다른계정"
        assertEquals("다른계정", fixture.repository.getProfile().nickname)
        assertEquals(2, fixture.getCount)
        fixture.client.close()
    }

    @Test
    fun `세션 복원과 같은 세션의 토큰 변경은 프로필 캐시를 유지한다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        fixture.tokens.save(AuthTokens("refreshed", "refreshed-refresh"))
        fixture.tokens.restoreAuthSession(fixture.session)
        fixture.repository.getProfile()
        assertEquals(1, fixture.getCount)
        fixture.client.close()
    }

    @Test
    fun `재인증이 필요해지면 화면과 관계없이 캐시를 무효화한다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        fixture.session.requireReauthentication()
        assertNull(fixture.repository.getCachedProfile())
        fixture.client.close()
    }

    @Test
    fun `계정 전환 후 늦게 도착한 이전 조회 응답은 저장하거나 반환하지 않는다`() = runTest {
        val fixture = ProfileFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeGet = { withContext(NonCancellable) { gate.await() } }
        val old = async { fixture.repository.getProfile() }
        runCurrent()
        fixture.session.clear()
        fixture.session.startNewSession()
        fixture.nickname = "새계정"
        fixture.beforeGet = {}
        assertEquals("새계정", fixture.repository.getProfile().nickname)
        gate.complete(Unit)
        assertFailsWith<CancellationException> { old.await() }
        assertEquals("새계정", fixture.repository.getCachedProfile()?.nickname)
        fixture.client.close()
    }

    @Test
    fun `닉네임 수정은 서버가 반환한 닉네임으로 캐시를 갱신한다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        fixture.repository.updateNickname("입력한닉네임")
        assertEquals("서버닉네임", fixture.repository.getCachedProfile()?.nickname)
        assertEquals("서버닉네임", fixture.repository.getProfile().nickname)
        assertEquals(1, fixture.getCount)
        fixture.client.close()
    }

    @Test
    fun `닉네임 수정 전에 시작한 조회가 늦게 끝나도 수정한 닉네임을 유지한다`() = runTest {
        val fixture = ProfileFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeGet = { gate.await() }
        val pending = async { fixture.repository.getProfile() }
        runCurrent()
        fixture.repository.updateNickname("입력한닉네임")
        gate.complete(Unit)
        assertEquals("서버닉네임", pending.await().nickname)
        assertEquals("서버닉네임", fixture.repository.getCachedProfile()?.nickname)
        fixture.client.close()
    }

    @Test
    fun `탈퇴 성공 시 프로필 캐시를 삭제한다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.repository.getProfile()
        fixture.repository.withdraw()
        assertNull(fixture.repository.getCachedProfile())
        fixture.client.close()
    }

    @Test
    fun `조회 실패는 캐시하지 않고 다음 요청에서 다시 조회한다`() = runTest {
        val fixture = ProfileFixture(this)
        fixture.failGet = true
        assertFailsWith<IllegalStateException> { fixture.repository.getProfile() }
        assertNull(fixture.repository.getCachedProfile())
        fixture.failGet = false
        assertEquals("원래닉네임", fixture.repository.getProfile().nickname)
        assertEquals(2, fixture.getCount)
        fixture.client.close()
    }
}

private class ProfileFixture(scope: TestScope) {
    val session = AuthSession().apply { markAuthenticated() }
    val tokens = ProfileTokenStorage()
    var getCount = 0
    var nickname = "원래닉네임"
    var failGet = false
    var failMutation = false
    var expireNextPatch = false
    var reissueCount = 0
    var beforeGet: suspend () -> Unit = {}
    var beforePatch: suspend () -> Unit = {}
    val client = HttpClient(MockEngine { request ->
        if (request.url.encodedPath.endsWith("/reissue")) {
            reissueCount++
            return@MockEngine respond(
                """{"isSuccess":true,"code":"OK","message":"성공","result":{"accessToken":"renewed","refreshToken":"renewed-refresh"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        if (request.method == HttpMethod.Patch && expireNextPatch) {
            expireNextPatch = false
            return@MockEngine respond(
                """{"isSuccess":false,"code":"AUTH401","message":"만료","result":null}""",
                HttpStatusCode.Unauthorized,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        if (request.method != HttpMethod.Get && failMutation) {
            return@MockEngine respond(
                """{"isSuccess":false,"code":"ERROR","message":"실패","result":null}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val result = when (request.method) {
            HttpMethod.Get -> {
                getCount++
                val requestedNickname = nickname
                beforeGet()
                if (failGet) {
                    return@MockEngine respond(
                        """{"isSuccess":false,"code":"ERROR","message":"실패","result":null}""",
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                """{"nickname":"$requestedNickname","email":"member@example.com"}"""
            }
            HttpMethod.Patch -> {
                beforePatch()
                """{"nickname":"서버닉네임"}"""
            }
            HttpMethod.Delete -> "null"
            else -> error("예상하지 않은 요청")
        }
        respond(
            """{"isSuccess":true,"code":"OK","message":"성공","result":$result}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }) { configureRingoutHttpClient() }
    val repository = DefaultMemberRepository(client, tokens, session, scope.backgroundScope)
}

private class ProfileTokenStorage : SecureTokenStorage {
    private var tokens: AuthTokens? = AuthTokens("access", "refresh")
    override suspend fun read(): AuthTokens? = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}
