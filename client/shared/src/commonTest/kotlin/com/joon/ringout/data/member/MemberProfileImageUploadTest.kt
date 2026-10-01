package com.joon.ringout.data.member

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.member.MemberProfileImage
import com.joon.ringout.domain.member.ProfileImageUpload
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemberProfileImageUploadTest {
    @Test
    fun `원본 파일을 인증된 멀티파트 image 필드로 전송하고 반환된 사진을 캐시한다`() = runTest {
        val fixture = UploadFixture(this)
        fixture.repository.getProfileImage()
        val saved = fixture.repository.uploadProfileImage(Photo)
        val type = fixture.uploadContentType.orEmpty()
        assertTrue(type.startsWith("multipart/form-data; boundary="))
        val body = fixture.uploadBody.orEmpty()
        assertTrue(body.contains("name=\"image\""))
        assertTrue(body.contains("filename=\"profile.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg", ignoreCase = true))
        assertTrue(body.contains("original-file-bytes"))
        assertEquals(MemberProfileImage(NewUrl), saved)
        assertEquals(saved, fixture.repository.getCachedProfileImage())
        assertEquals(saved, fixture.repository.getProfileImage())
        assertEquals(1, fixture.getCount)
        fixture.client.close()
    }

    @Test
    fun `업로드 실패 시 기존 사진 캐시를 보존한다`() = runTest {
        val fixture = UploadFixture(this)
        val original = fixture.repository.getProfileImage()
        fixture.failUpload = true
        val error = assertFailsWith<ApiException> { fixture.repository.uploadProfileImage(Photo) }
        assertEquals("FILE500", error.code)
        assertEquals(original, fixture.repository.getCachedProfileImage())
        fixture.client.close()
    }

    @Test
    fun `업로드 전에 시작한 조회는 저장된 새 사진을 덮어쓰지 않는다`() = runTest {
        val fixture = UploadFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeGet = { gate.await() }
        val pending = async { fixture.repository.getProfileImage() }
        fixture.getStarted.await()
        fixture.repository.uploadProfileImage(Photo)
        gate.complete(Unit)
        assertEquals(MemberProfileImage(NewUrl), pending.await())
        assertEquals(MemberProfileImage(NewUrl), fixture.repository.getCachedProfileImage())
        fixture.client.close()
    }

    @Test
    fun `업로드 응답 전에 계정이 바뀌면 새 계정 캐시를 변경하지 않는다`() = runTest {
        val fixture = UploadFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeUpload = { gate.await() }
        val pending = async { fixture.repository.uploadProfileImage(Photo) }
        fixture.uploadStarted.await()
        fixture.session.startNewSession()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertNull(fixture.repository.getCachedProfileImage())
        fixture.client.close()
    }

    @Test
    fun `탈퇴 후 도착한 업로드 응답은 사진 캐시를 다시 채우지 않는다`() = runTest {
        val fixture = UploadFixture(this)
        val gate = CompletableDeferred<Unit>()
        fixture.beforeUpload = { withContext(NonCancellable) { gate.await() } }
        val pending = async { fixture.repository.uploadProfileImage(Photo) }
        fixture.uploadStarted.await()
        fixture.repository.withdraw()
        gate.complete(Unit)
        assertFailsWith<CancellationException> { pending.await() }
        assertNull(fixture.repository.getCachedProfileImage())
        fixture.client.close()
    }

    @Test
    fun `빈 파일과 제한 초과 파일은 업로드 요청 전에 거부한다`() {
        assertFailsWith<IllegalArgumentException> { ProfileImageUpload(byteArrayOf(), "image/jpeg", "p.jpg") }
        assertFailsWith<IllegalArgumentException> {
            ProfileImageUpload(ByteArray(5 * 1024 * 1024 + 1), "image/jpeg", "p.jpg")
        }
    }
}

private val Photo = ProfileImageUpload("original-file-bytes".encodeToByteArray(), "image/jpeg", "profile.jpg")
private const val NewUrl = "https://example.com/new.jpg"

private class UploadFixture(scope: TestScope) {
    val session = AuthSession().apply { markAuthenticated() }
    val getStarted = CompletableDeferred<Unit>()
    val uploadStarted = CompletableDeferred<Unit>()
    var getCount = 0
    var failUpload = false
    var uploadContentType: String? = null
    var uploadBody: String? = null
    var beforeGet: suspend () -> Unit = {}
    var beforeUpload: suspend () -> Unit = {}
    val client = HttpClient(MockEngine { request ->
        assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
        if (request.method != HttpMethod.Delete) assertEquals("/api/v1/user/profile-image", request.url.encodedPath)
        val result = when (request.method) {
            HttpMethod.Get -> {
                getCount++
                getStarted.complete(Unit)
                beforeGet()
                """{"profileImageUrl":"https://example.com/old.jpg"}"""
            }
            HttpMethod.Post -> {
                uploadContentType = request.body.contentType.toString()
                uploadBody = coroutineScope {
                    val channel = ByteChannel()
                    val writer = launch {
                        (request.body as OutgoingContent.WriteChannelContent).writeTo(channel)
                        channel.close()
                    }
                    val bytes = channel.readRemaining().readByteArray()
                    writer.join()
                    bytes.decodeToString()
                }
                uploadStarted.complete(Unit)
                beforeUpload()
                if (failUpload) return@MockEngine respond(
                    """{"isSuccess":false,"code":"FILE500","message":"업로드 실패","result":null}""",
                    HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
                """{"profileImageUrl":"$NewUrl"}"""
            }
            HttpMethod.Delete -> "null"
            else -> error("예상하지 않은 요청")
        }
        respond(
            """{"isSuccess":true,"code":"USER201","message":"성공","result":$result}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
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
