package com.joon.ringout.data.member

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DefaultMemberRepositoryTest {
    @Test
    fun `회원 탈퇴 요청에 Ringout access token을 전송한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/v1/users/me", request.url.encodedPath)
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "COMMON200",
                      "message": "성공입니다.",
                      "result": null
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val repository = DefaultMemberRepository(client, FakeTokenStorage())

        repository.withdraw()

        client.close()
    }

    @Test
    fun `닉네임 수정 요청에 Ringout access token을 전송한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/api/v1/users/me/nickname", request.url.encodedPath)
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            assertTrue((request.body as TextContent).text.contains("\"nickname\":\"새닉네임\""))
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "COMMON200",
                      "message": "성공입니다.",
                      "result": { "nickname": "새닉네임" }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val repository = DefaultMemberRepository(client, FakeTokenStorage())

        val nickname = repository.updateNickname("새닉네임")

        assertEquals("새닉네임", nickname)
        client.close()
    }

    @Test
    fun `서버 오류 메시지를 전달한다`() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = """
                    {
                      "isSuccess": false,
                      "code": "COMMON400",
                      "message": "잘못된 요청입니다."
                    }
                """.trimIndent(),
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val repository = DefaultMemberRepository(client, FakeTokenStorage())

        val exception = assertFailsWith<ApiException> {
            repository.updateNickname("새닉네임")
        }

        assertEquals("잘못된 요청입니다.", exception.message)
        client.close()
    }
}

private class FakeTokenStorage : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) = Unit

    override suspend fun read(): AuthTokens = AuthTokens(
        accessToken = "ringout-access",
        refreshToken = "ringout-refresh",
    )

    override suspend fun clear() = Unit
}
