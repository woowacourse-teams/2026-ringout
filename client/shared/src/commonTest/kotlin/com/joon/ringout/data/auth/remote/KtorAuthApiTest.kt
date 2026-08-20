package com.joon.ringout.data.auth.remote

import com.joon.ringout.data.auth.remote.model.LoginRequest
import com.joon.ringout.data.auth.remote.model.ReissueRequest
import com.joon.ringout.data.auth.remote.model.SignupRequest
import com.joon.ringout.data.auth.remote.model.TermsType
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.configureRingoutHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorAuthApiTest {
    @Test
    fun `Apple 로그인 요청에 ID Token을 기존 social access token 필드로 전송한다`() = runTest {
        val api = authApi { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/auth/apple/login", request.url.encodedPath)
            assertTrue(request.bodyText().contains("\"socialAccessToken\":\"apple-id-token\""))
            respondJson(
                """
                {
                  "isSuccess": true,
                  "code": "AUTH200",
                  "message": "로그인에 성공했습니다.",
                  "result": {
                    "accessToken": "access-token",
                    "refreshToken": "refresh-token",
                    "isNewUser": false
                  }
                }
                """.trimIndent(),
            )
        }

        val response = api.login(
            provider = AuthProvider.Apple,
            request = LoginRequest(socialAccessToken = "apple-id-token"),
        )

        assertEquals("access-token", response.result?.accessToken)
        assertEquals(false, response.result?.isNewUser)
    }

    @Test
    fun `카카오 로그인 요청을 보내고 응답을 변환한다`() = runTest {
        val api = authApi { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/auth/kakao/login", request.url.encodedPath)
            assertTrue(request.bodyText().contains("\"socialAccessToken\":\"kakao-token\""))
            respondJson(
                """
                {
                  "isSuccess": true,
                  "code": "AUTH200",
                  "message": "로그인에 성공했습니다.",
                  "result": {
                    "accessToken": "access-token",
                    "refreshToken": "refresh-token",
                    "isNewUser": false
                  }
                }
                """.trimIndent(),
            )
        }

        val response = api.login(
            provider = AuthProvider.Kakao,
            request = LoginRequest(socialAccessToken = "kakao-token"),
        )

        assertEquals("access-token", response.result?.accessToken)
        assertEquals(false, response.result?.isNewUser)
    }

    @Test
    fun `회원가입 요청에 signup token과 약관 정보를 전송한다`() = runTest {
        val api = authApi { request ->
            assertEquals("Bearer signup-token", request.headers[HttpHeaders.Authorization])
            assertEquals("/api/v1/auth/signup", request.url.encodedPath)
            assertTrue(request.bodyText().contains("\"SERVICE\""))
            assertTrue(request.bodyText().contains("\"PRIVACY\""))
            respondJson(
                """
                {
                  "isSuccess": true,
                  "code": "AUTH201",
                  "message": "회원가입에 성공했습니다.",
                  "result": {
                    "accessToken": "access-token",
                    "refreshToken": "refresh-token"
                  }
                }
                """.trimIndent(),
                status = HttpStatusCode.Created,
            )
        }

        val response = api.signup(
            signupToken = "signup-token",
            request = SignupRequest(
                termsTypes = listOf(TermsType.SERVICE, TermsType.PRIVACY),
                agreedAt = "2026-08-16",
            ),
        )

        assertEquals("refresh-token", response.result?.refreshToken)
    }

    @Test
    fun `토큰 재발급 요청에 refresh token을 전송하고 새 토큰을 변환한다`() = runTest {
        val api = authApi { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/auth/reissue", request.url.encodedPath)
            assertTrue(request.bodyText().contains("\"refreshToken\":\"old-refresh\""))
            respondJson(
                """
                {
                  "isSuccess": true,
                  "code": "COMMON200",
                  "message": "성공입니다.",
                  "result": {
                    "accessToken": "new-access",
                    "refreshToken": "new-refresh"
                  }
                }
                """.trimIndent(),
            )
        }

        val response = api.reissue(ReissueRequest(refreshToken = "old-refresh"))

        assertEquals("new-access", response.result?.accessToken)
        assertEquals("new-refresh", response.result?.refreshToken)
    }

    @Test
    fun `토큰 재발급의 COMMON401 응답을 ApiException으로 변환한다`() = runTest {
        val api = authApi {
            respondJson(
                """
                {
                  "isSuccess": false,
                  "code": "COMMON401",
                  "message": "인증이 필요합니다."
                }
                """.trimIndent(),
                status = HttpStatusCode.Unauthorized,
            )
        }

        val exception = assertFailsWith<ApiException> {
            api.reissue(ReissueRequest(refreshToken = "invalid-refresh"))
        }

        assertEquals(HttpStatusCode.Unauthorized.value, exception.statusCode)
        assertEquals("COMMON401", exception.code)
        assertEquals("인증이 필요합니다.", exception.apiMessage)
    }

    @Test
    fun `빈 refresh token은 재발급 요청 전에 거부한다`() {
        assertFailsWith<IllegalArgumentException> {
            ReissueRequest(refreshToken = " ")
        }
    }

    @Test
    fun `서버 오류 응답을 ApiException으로 변환한다`() = runTest {
        val api = authApi {
            respondJson(
                """
                {
                  "isSuccess": false,
                  "code": "AUTH409",
                  "message": "이미 가입한 회원입니다."
                }
                """.trimIndent(),
                status = HttpStatusCode.Conflict,
            )
        }

        val exception = assertFailsWith<ApiException> {
            api.signup(
                signupToken = "signup-token",
                request = SignupRequest(
                    termsTypes = listOf(TermsType.SERVICE, TermsType.PRIVACY),
                    agreedAt = "2026-08-16",
                ),
            )
        }

        assertEquals(409, exception.statusCode)
        assertEquals("AUTH409", exception.code)
        assertEquals("이미 가입한 회원입니다.", exception.apiMessage)
    }
}

private fun authApi(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): AuthApi {
    val client = HttpClient(MockEngine(handler)) {
        configureRingoutHttpClient()
    }
    return KtorAuthApi(client)
}

private fun MockRequestHandleScope.respondJson(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = content,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

private fun HttpRequestData.bodyText(): String = when (val content = body) {
    is TextContent -> content.text
    else -> content.toString()
}
