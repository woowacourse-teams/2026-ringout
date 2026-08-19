package com.joon.ringout.data.auth

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AuthenticatedRequestExecutorTest {
    @Test
    fun `AUTH401이면 토큰을 재발급하고 새 access token으로 원래 요청을 재시도한다`() = runTest {
        var protectedRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> {
                    protectedRequestCount++
                    when (request.headers[HttpHeaders.Authorization]) {
                        "Bearer ${OldTokens.accessToken}" -> respondJson(
                            content = Auth401Response,
                            status = HttpStatusCode.Unauthorized,
                        )

                        "Bearer ${NewTokens.accessToken}" -> respondJson(SuccessResponse)
                        else -> error("예상하지 않은 Authorization 헤더입니다.")
                    }
                }

                ReissuePath -> {
                    reissueRequestCount++
                    assertEquals(
                        "{\"refreshToken\":\"${OldTokens.refreshToken}\"}",
                        request.bodyText(),
                    )
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val response = executor.execute(client::getProtected)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, protectedRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(NewTokens, storage.tokens)
        assertEquals(1, storage.saveCount)
        assertEquals(0, storage.clearCount)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `최초 요청이 COMMON401이면 재발급하지 않고 세션을 만료시킨다`() = runTest {
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> respondJson(
                    content = Common401Response,
                    status = HttpStatusCode.Unauthorized,
                )

                ReissuePath -> {
                    reissueRequestCount++
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val exception = assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals("COMMON401", exception.code)
        assertEquals(0, reissueRequestCount)
        assertEquals(1, storage.clearCount)
        assertEquals(null, storage.tokens)
        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
        client.close()
    }

    @Test
    fun `COMMON401 만료 처리 중 요청이 취소돼도 토큰과 세션을 함께 만료시킨다`() = runTest {
        val expireStarted = CompletableDeferred<Unit>()
        val allowExpireCompletion = CompletableDeferred<Unit>()
        val storage = RecordingTokenStorage(
            tokens = OldTokens,
            onExpire = {
                expireStarted.complete(Unit)
                allowExpireCompletion.await()
            },
        )
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> respondJson(
                    content = Common401Response,
                    status = HttpStatusCode.Unauthorized,
                )

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val request = async {
            try {
                executor.execute(client::getProtected)
            } catch (_: Throwable) {
                null
            }
        }
        expireStarted.await()
        request.cancel()
        allowExpireCompletion.complete(Unit)
        request.join()

        assertEquals(null, storage.tokens)
        assertEquals(1, storage.clearCount)
        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
        client.close()
    }

    @Test
    fun `HTTP 401이 아닌 AUTH401 응답은 재발급하지 않는다`() = runTest {
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> respondJson(
                    content = Auth401Response,
                    status = HttpStatusCode.Forbidden,
                )

                ReissuePath -> {
                    reissueRequestCount++
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val response = executor.execute(client::getProtected)

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(0, reissueRequestCount)
        assertEquals(OldTokens, storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `재발급이 401로 실패하면 토큰을 삭제하고 원래 요청을 재시도하지 않는다`() = runTest {
        var protectedRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> {
                    protectedRequestCount++
                    respondJson(
                        content = Auth401Response,
                        status = HttpStatusCode.Unauthorized,
                    )
                }

                ReissuePath -> {
                    reissueRequestCount++
                    respondJson(
                        content = Auth401Response,
                        status = HttpStatusCode.Unauthorized,
                    )
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val exception = assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals("AUTH401", exception.code)
        assertEquals(1, protectedRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(null, storage.tokens)
        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
        client.close()
    }

    @Test
    fun `재발급 실패 후 저장소 만료 처리도 실패하면 cleanup 오류를 안전하게 전달한다`() = runTest {
        val storage = FailingExpireTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> respondJson(
                    content = Auth401Response,
                    status = HttpStatusCode.Unauthorized,
                )

                ReissuePath -> respondJson(
                    content = Common401Response,
                    status = HttpStatusCode.Unauthorized,
                )

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val exception = assertFailsWith<IllegalStateException> {
            executor.execute(client::getProtected)
        }

        assertEquals("secure storage failure", exception.message)
        assertEquals(OldTokens, storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `재발급이 HTTP 200 COMMON401로 실패해도 토큰과 세션을 만료시킨다`() = runTest {
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> respondJson(
                    content = Auth401Response,
                    status = HttpStatusCode.Unauthorized,
                )

                ReissuePath -> respondJson(Common401Response)
                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val exception = assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals("COMMON401", exception.code)
        assertEquals(null, storage.tokens)
        assertEquals(1, storage.clearCount)
        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
        client.close()
    }

    @Test
    fun `재발급 서버 오류는 토큰과 로그인 상태를 유지한다`() = runTest {
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> respondJson(
                    content = Auth401Response,
                    status = HttpStatusCode.Unauthorized,
                )

                ReissuePath -> respondJson(
                    content = ServerErrorResponse,
                    status = HttpStatusCode.InternalServerError,
                )

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val exception = assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals(HttpStatusCode.InternalServerError.value, exception.statusCode)
        assertEquals(OldTokens, storage.tokens)
        assertEquals(0, storage.clearCount)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `재시도도 AUTH401이면 재발급을 반복하지 않는다`() = runTest {
        var protectedRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> {
                    protectedRequestCount++
                    respondJson(
                        content = Auth401Response,
                        status = HttpStatusCode.Unauthorized,
                    )
                }

                ReissuePath -> {
                    reissueRequestCount++
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals(2, protectedRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(null, storage.tokens)
        assertEquals(AuthSessionState.ReauthenticationRequired, session.state.value)
        client.close()
    }

    @Test
    fun `동시에 AUTH401을 받은 요청은 재발급 한 번과 새 토큰을 공유한다`() = runTest {
        val countMutex = Mutex()
        val bothOldRequestsArrived = CompletableDeferred<Unit>()
        var oldRequestCount = 0
        var newRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> when (request.headers[HttpHeaders.Authorization]) {
                    "Bearer ${OldTokens.accessToken}" -> {
                        countMutex.withLock {
                            oldRequestCount++
                            if (oldRequestCount == 2) {
                                bothOldRequestsArrived.complete(Unit)
                            }
                        }
                        bothOldRequestsArrived.await()
                        respondJson(
                            content = Auth401Response,
                            status = HttpStatusCode.Unauthorized,
                        )
                    }

                    "Bearer ${NewTokens.accessToken}" -> {
                        countMutex.withLock { newRequestCount++ }
                        respondJson(SuccessResponse)
                    }

                    else -> error("예상하지 않은 Authorization 헤더입니다.")
                }

                ReissuePath -> {
                    countMutex.withLock { reissueRequestCount++ }
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val firstExecutor = AuthenticatedRequestExecutor(client, storage, session)
        val secondExecutor = AuthenticatedRequestExecutor(client, storage, session)

        val responses = coroutineScope {
            listOf(
                async { firstExecutor.execute(client::getProtected) },
                async { secondExecutor.execute(client::getProtected) },
            ).awaitAll()
        }

        assertEquals(2, responses.size)
        assertEquals(2, oldRequestCount)
        assertEquals(2, newRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(NewTokens, storage.tokens)
        client.close()
    }

    @Test
    fun `동시에 AUTH401을 받은 요청은 실패한 재발급도 한 번만 공유한다`() = runTest {
        val countMutex = Mutex()
        val bothOldRequestsArrived = CompletableDeferred<Unit>()
        val secondRefreshDecisionRead = CompletableDeferred<Unit>()
        var oldRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(
            tokens = OldTokens,
            onRead = { readCount ->
                if (readCount == 4) {
                    secondRefreshDecisionRead.complete(Unit)
                }
            },
        )
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> {
                    countMutex.withLock {
                        oldRequestCount++
                        if (oldRequestCount == 2) {
                            bothOldRequestsArrived.complete(Unit)
                        }
                    }
                    bothOldRequestsArrived.await()
                    respondJson(
                        content = Auth401Response,
                        status = HttpStatusCode.Unauthorized,
                    )
                }

                ReissuePath -> {
                    countMutex.withLock { reissueRequestCount++ }
                    secondRefreshDecisionRead.await()
                    respondJson(
                        content = ServerErrorResponse,
                        status = HttpStatusCode.InternalServerError,
                    )
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val firstExecutor = AuthenticatedRequestExecutor(client, storage, session)
        val secondExecutor = AuthenticatedRequestExecutor(client, storage, session)

        val exceptions = coroutineScope {
            val requests = listOf(
                async {
                    captureApiException {
                        firstExecutor.execute(client::getProtected)
                    }
                },
                async {
                    captureApiException {
                        secondExecutor.execute(client::getProtected)
                    }
                },
            )
            requests.awaitAll()
        }

        assertEquals(listOf("COMMON500", "COMMON500"), exceptions.map(ApiException::code))
        assertEquals(2, oldRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(OldTokens, storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `재발급을 시작한 요청이 취소돼도 다른 요청은 같은 재발급 결과를 받는다`() = runTest {
        val reissueStarted = CompletableDeferred<Unit>()
        val allowReissueResponse = CompletableDeferred<Unit>()
        var oldRequestCount = 0
        var newRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> when (request.headers[HttpHeaders.Authorization]) {
                    "Bearer ${OldTokens.accessToken}" -> {
                        oldRequestCount++
                        respondJson(
                            content = Auth401Response,
                            status = HttpStatusCode.Unauthorized,
                        )
                    }

                    "Bearer ${NewTokens.accessToken}" -> {
                        newRequestCount++
                        respondJson(SuccessResponse)
                    }

                    else -> error("예상하지 않은 Authorization 헤더입니다.")
                }

                ReissuePath -> {
                    reissueRequestCount++
                    reissueStarted.complete(Unit)
                    allowReissueResponse.await()
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val firstExecutor = AuthenticatedRequestExecutor(client, storage, session)
        val secondExecutor = AuthenticatedRequestExecutor(client, storage, session)

        val response = coroutineScope {
            val starter = async { firstExecutor.execute(client::getProtected) }
            reissueStarted.await()
            val waiter = async { secondExecutor.execute(client::getProtected) }
            yield()
            starter.cancelAndJoin()
            allowReissueResponse.complete(Unit)
            waiter.await()
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, oldRequestCount)
        assertEquals(1, newRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(NewTokens, storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `요청 중 계정이 바뀌면 새 사용자의 토큰으로 이전 요청을 재시도하지 않는다`() = runTest {
        var protectedRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> {
                    protectedRequestCount++
                    assertEquals(
                        "Bearer ${OldTokens.accessToken}",
                        request.headers[HttpHeaders.Authorization],
                    )
                    storage.replaceAuthTokens(OtherUserTokens, session)
                    respondJson(
                        content = Auth401Response,
                        status = HttpStatusCode.Unauthorized,
                    )
                }

                ReissuePath -> {
                    reissueRequestCount++
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals(1, protectedRequestCount)
        assertEquals(0, reissueRequestCount)
        assertEquals(OtherUserTokens, storage.tokens)
        assertEquals(0, storage.clearCount)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `AUTH401 처리 전에 로그아웃하면 세션 만료 상태로 덮어쓰지 않는다`() = runTest {
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> {
                    storage.removeAuthTokens(session)
                    respondJson(
                        content = Auth401Response,
                        status = HttpStatusCode.Unauthorized,
                    )
                }

                ReissuePath -> {
                    reissueRequestCount++
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals(0, reissueRequestCount)
        assertEquals(null, storage.tokens)
        assertEquals(AuthSessionState.Unauthenticated, session.state.value)
        client.close()
    }

    @Test
    fun `재발급과 새 로그인이 겹치면 새 사용자의 토큰이 최종 저장된다`() = runTest {
        val reissueStarted = CompletableDeferred<Unit>()
        val allowReissueResponse = CompletableDeferred<Unit>()
        var protectedRequestCount = 0
        var reissueRequestCount = 0
        val storage = RecordingTokenStorage(OldTokens)
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> when (request.headers[HttpHeaders.Authorization]) {
                    "Bearer ${OldTokens.accessToken}" -> {
                        protectedRequestCount++
                        respondJson(
                            content = Auth401Response,
                            status = HttpStatusCode.Unauthorized,
                        )
                    }

                    "Bearer ${NewTokens.accessToken}" -> {
                        protectedRequestCount++
                        respondJson(SuccessResponse)
                    }

                    else -> error("예상하지 않은 Authorization 헤더입니다.")
                }

                ReissuePath -> {
                    reissueRequestCount++
                    reissueStarted.complete(Unit)
                    allowReissueResponse.await()
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val exception = coroutineScope {
            val request = async {
                try {
                    executor.execute(client::getProtected)
                    error("ApiException이 발생해야 합니다.")
                } catch (error: ApiException) {
                    error
                }
            }
            reissueStarted.await()
            storage.replaceAuthTokens(OtherUserTokens, session)
            allowReissueResponse.complete(Unit)
            request.await()
        }

        assertEquals("AUTH401", exception.code)
        assertEquals(1, protectedRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(OtherUserTokens, storage.tokens)
        assertEquals(0, storage.clearCount)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }

    @Test
    fun `재발급 완료 직후 계정이 바뀌면 이전 요청을 재시도하지 않는다`() = runTest {
        var protectedRequestCount = 0
        var reissueRequestCount = 0
        lateinit var storage: RecordingTokenStorage
        storage = RecordingTokenStorage(
            tokens = OldTokens,
            onRead = { readCount ->
                if (readCount == 4) {
                    storage.tokens = OtherUserTokens
                }
            },
        )
        val session = authenticatedSession()
        val client = mockClient { request ->
            when (request.url.encodedPath) {
                ProtectedPath -> {
                    protectedRequestCount++
                    assertEquals(
                        "Bearer ${OldTokens.accessToken}",
                        request.headers[HttpHeaders.Authorization],
                    )
                    respondJson(
                        content = Auth401Response,
                        status = HttpStatusCode.Unauthorized,
                    )
                }

                ReissuePath -> {
                    reissueRequestCount++
                    respondJson(ReissueSuccessResponse)
                }

                else -> error("예상하지 않은 요청입니다: ${request.url}")
            }
        }
        val executor = AuthenticatedRequestExecutor(client, storage, session)

        val exception = assertFailsWith<ApiException> {
            executor.execute(client::getProtected)
        }

        assertEquals("AUTH401", exception.code)
        assertEquals(1, protectedRequestCount)
        assertEquals(1, reissueRequestCount)
        assertEquals(OtherUserTokens, storage.tokens)
        assertEquals(AuthSessionState.Authenticated, session.state.value)
        client.close()
    }
}

private fun authenticatedSession(): AuthSession = AuthSession().apply {
    markAuthenticated()
}

private fun mockClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
): HttpClient = HttpClient(MockEngine(handler)) {
    configureRingoutHttpClient()
}

private suspend fun HttpClient.getProtected(accessToken: String): HttpResponse =
    get(ApiConfig.url(ProtectedPath)) {
        bearerAuth(accessToken)
    }

private suspend fun captureApiException(block: suspend () -> Unit): ApiException = try {
    block()
    error("ApiException이 발생해야 합니다.")
} catch (error: ApiException) {
    error
}

private fun MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

private fun HttpRequestData.bodyText(): String = assertNotNull(body as? TextContent).text

private class RecordingTokenStorage(
    var tokens: AuthTokens?,
    private val onRead: (Int) -> Unit = {},
    private val onExpire: suspend () -> Unit = {},
) : SecureTokenStorage {
    var saveCount: Int = 0
    var clearCount: Int = 0
    private var reauthenticationRequired: Boolean = false
    private val readCountMutex = Mutex()
    private var readCount: Int = 0

    override suspend fun save(tokens: AuthTokens) {
        saveCount++
        this.tokens = tokens
        reauthenticationRequired = false
    }

    override suspend fun read(): AuthTokens? {
        val currentReadCount = readCountMutex.withLock {
            readCount += 1
            readCount
        }
        onRead(currentReadCount)
        return tokens
    }

    override suspend fun clear() {
        clearCount++
        tokens = null
        reauthenticationRequired = false
    }

    override suspend fun expire() {
        clearCount++
        tokens = null
        onExpire()
        reauthenticationRequired = true
    }

    override suspend fun isReauthenticationRequired(): Boolean = reauthenticationRequired
}

private class FailingExpireTokenStorage(
    var tokens: AuthTokens?,
) : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun read(): AuthTokens? = tokens

    override suspend fun clear() {
        tokens = null
    }

    override suspend fun expire(): Nothing = error("secure storage failure")
}

private val OldTokens = AuthTokens(
    accessToken = "expired-access-token",
    refreshToken = "valid-refresh-token",
)
private val NewTokens = AuthTokens(
    accessToken = "new-access-token",
    refreshToken = "new-refresh-token",
)
private val OtherUserTokens = AuthTokens(
    accessToken = "other-user-access-token",
    refreshToken = "other-user-refresh-token",
)

private const val ProtectedPath = "/api/v1/protected"
private const val ReissuePath = "/api/v1/auth/reissue"

private const val SuccessResponse =
    """{"isSuccess":true,"code":"COMMON200","message":"성공입니다.","result":null}"""
private const val Auth401Response =
    """{"isSuccess":false,"code":"AUTH401","message":"액세스 토큰이 만료되었습니다."}"""
private const val Common401Response =
    """{"isSuccess":false,"code":"COMMON401","message":"인증이 필요합니다."}"""
private const val ReissueSuccessResponse =
    """{"isSuccess":true,"code":"COMMON200","message":"성공입니다.","result":{"accessToken":"new-access-token","refreshToken":"new-refresh-token"}}"""
private const val ServerErrorResponse =
    """{"isSuccess":false,"code":"COMMON500","message":"서버 오류입니다."}"""
