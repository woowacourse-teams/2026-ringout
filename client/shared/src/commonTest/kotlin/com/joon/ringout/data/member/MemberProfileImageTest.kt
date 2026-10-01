package com.joon.ringout.data.member

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.member.MemberProfileImage
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

@OptIn(ExperimentalCoroutinesApi::class)
class MemberProfileImageTest {
    @Test
    fun `인증된 이미지 조회 결과를 같은 세션에서 재사용한다`() = runTest {
        val fixture = ImageFixture(this)
        assertNull(fixture.repository.getCachedProfileImage())
        repeat(3) {
            assertEquals(MemberProfileImage(ImageUrl), fixture.repository.getProfileImage())
        }
        assertEquals(MemberProfileImage(ImageUrl), fixture.repository.getCachedProfileImage())
        assertEquals(1, fixture.requestCount)
        fixture.client.close()
    }

    @Test
    fun `등록된 이미지가 없다는 성공 응답도 캐시한다`() = runTest {
        val fixture = ImageFixture(this).apply { result = """{"profileImageUrl":null}""" }
        repeat(2) { assertEquals(MemberProfileImage(null), fixture.repository.getProfileImage()) }
        assertEquals(MemberProfileImage(null), fixture.repository.getCachedProfileImage())
        assertEquals(1, fixture.requestCount)
        fixture.client.close()
    }

    @Test
    fun `서버 오류는 캐시하지 않아 다음 조회에서 재시도한다`() = runTest {
        val fixture = ImageFixture(this).apply { status = HttpStatusCode.InternalServerError }
        val error = assertFailsWith<ApiException> { fixture.repository.getProfileImage() }
        assertEquals("FILE500_1", error.code)
        assertNull(fixture.repository.getCachedProfileImage())
        fixture.status = HttpStatusCode.OK
        assertEquals(MemberProfileImage(ImageUrl), fixture.repository.getProfileImage())
        assertEquals(2, fixture.requestCount)
        fixture.client.close()
    }

    @Test
    fun `결과 객체가 누락된 응답을 이미지 없음으로 캐시하지 않는다`() = runTest {
        val fixture = ImageFixture(this).apply { result = "null" }
        assertFailsWith<IllegalStateException> { fixture.repository.getProfileImage() }
        assertNull(fixture.repository.getCachedProfileImage())
        fixture.client.close()
    }

    @Test
    fun `동시 조회는 요청을 공유하며 호출자 취소와 무관하게 완료한다`() = runTest {
        val fixture = ImageFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeResponse = { gate.await() }
        val first = async { fixture.repository.getProfileImage() }
        val second = async { fixture.repository.getProfileImage() }
        runCurrent()
        fixture.requestStarted.await()
        assertEquals(1, fixture.requestCount)
        first.cancel()
        gate.complete(Unit)
        assertEquals(MemberProfileImage(ImageUrl), second.await())
        fixture.client.close()
    }

    @Test
    fun `계정 변경은 기존 사진 캐시를 지우고 새 사진을 조회한다`() = runTest {
        val fixture = ImageFixture(this)
        fixture.repository.getProfileImage()
        fixture.session.startNewSession()
        assertNull(fixture.repository.getCachedProfileImage())
        fixture.result = """{"profileImageUrl":"https://example.com/new.webp"}"""
        assertEquals(MemberProfileImage("https://example.com/new.webp"), fixture.repository.getProfileImage())
        assertEquals(2, fixture.requestCount)
        fixture.client.close()
    }

    @Test
    fun `세션 종료 후 도착한 사진은 캐시에 저장하지 않는다`() = runTest {
        val fixture = ImageFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeResponse = { withContext(NonCancellable) { gate.await() } }
        val pending = async { fixture.repository.getProfileImage() }
        fixture.requestStarted.await()
        fixture.session.clear()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertNull(fixture.repository.getCachedProfileImage())
        fixture.client.close()
    }

    @Test
    fun `탈퇴하면 사진 캐시를 지우고 늦은 응답으로 다시 채우지 않는다`() = runTest {
        val fixture = ImageFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeResponse = { withContext(NonCancellable) { gate.await() } }
        val pending = async { fixture.repository.getProfileImage() }
        fixture.requestStarted.await()
        fixture.repository.withdraw()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertNull(fixture.repository.getCachedProfileImage())
        fixture.client.close()
    }
}

private const val ImageUrl = "https://example.com/profile.webp"

private class ImageFixture(scope: TestScope) {
    val session = AuthSession().apply { markAuthenticated() }
    val requestStarted = CompletableDeferred<Unit>()
    var requestCount = 0
    var result = """{"profileImageUrl":"$ImageUrl"}"""
    var status = HttpStatusCode.OK
    var beforeResponse: suspend () -> Unit = {}
    val client = HttpClient(MockEngine { request ->
        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
        if (request.method == HttpMethod.Delete) {
            assertEquals("/api/v1/users/me", request.url.encodedPath)
            return@MockEngine respond(
                """{"isSuccess":true,"code":"USER200","message":"성공","result":null}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("/api/v1/user/profile-image", request.url.encodedPath)
        requestCount++
        requestStarted.complete(Unit)
        val capturedResult = result
        beforeResponse()
        respond(
            if (status == HttpStatusCode.OK) {
                """{"isSuccess":true,"code":"USER200","message":"성공","result":$capturedResult}"""
            } else {
                """{"isSuccess":false,"code":"FILE500_1","message":"이미지 조회 URL 생성에 실패했습니다.","result":null}"""
            },
            status,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }) { configureRingoutHttpClient() }
    val repository = DefaultMemberRepository(
        client,
        object : SecureTokenStorage {
            override suspend fun read() = AuthTokens("access", "refresh")
            override suspend fun save(tokens: AuthTokens) = Unit
            override suspend fun clear() = Unit
        },
        session,
        scope.backgroundScope,
    )
}
