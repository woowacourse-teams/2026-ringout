package com.joon.ringout.data.push

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.push.PushPlatform
import com.joon.ringout.domain.push.PushTokenRegistrationCandidate
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KtorPushTokenRegistrarTest {
    @Test
    fun `Android와 iOS 토큰을 명세에 맞는 JSON과 Bearer 인증으로 등록한다`() = runTest {
        for ((platform, apiPlatform) in listOf(
            PushPlatform.Android to "ANDROID",
            PushPlatform.Ios to "IOS",
        )) {
            val client = HttpClient(MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/v1/device-tokens", request.url.encodedPath)
                assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
                val requestContent = request.body as TextContent
                assertEquals(ContentType.Application.Json, requestContent.contentType)

                val requestBody = ApiJson.parseToJsonElement(requestContent.text).jsonObject
                assertEquals(setOf("platform", "fcmToken"), requestBody.keys)
                assertEquals(apiPlatform, requestBody.getValue("platform").jsonPrimitive.content)
                assertEquals("fcm-token-value", requestBody.getValue("fcmToken").jsonPrimitive.content)

                respond(
                    content = """{"isSuccess":true,"code":"PUSHALARM200","message":"디바이스 토큰이 등록되었습니다.","result":null}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders,
                )
            }) { configureRingoutHttpClient() }

            try {
                registrar(client).register(PushTokenRegistrationCandidate(platform, "fcm-token-value"))
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `HTTP 오류를 등록 성공으로 처리하지 않는다`() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = """{"isSuccess":false,"code":"COMMON400","message":"잘못된 요청입니다."}""",
                status = HttpStatusCode.BadRequest,
                headers = jsonHeaders,
            )
        }) { configureRingoutHttpClient() }

        try {
            val error = assertFailsWith<ApiException> {
                registrar(client).register(PushTokenRegistrationCandidate(PushPlatform.Android, "fcm-token-value"))
            }
            assertEquals(400, error.statusCode)
            assertEquals("COMMON400", error.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun `성공 상태 코드라도 API 응답이 실패면 등록 성공으로 처리하지 않는다`() = runTest {
        val client = HttpClient(MockEngine {
            respond(
                content = """{"isSuccess":false,"code":"PUSHALARM400","message":"등록에 실패했습니다."}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
        }) { configureRingoutHttpClient() }

        try {
            assertFailsWith<IllegalStateException> {
                registrar(client).register(PushTokenRegistrationCandidate(PushPlatform.Android, "fcm-token-value"))
            }
        } finally {
            client.close()
        }
    }
}

private fun registrar(client: HttpClient) = KtorPushTokenRegistrar(
    httpClient = client,
    tokenStorage = TestTokenStorage(),
    authSession = AuthSession().apply { markAuthenticated() },
)

private class TestTokenStorage : SecureTokenStorage {
    private var tokens: AuthTokens? = AuthTokens("ringout-access", "ringout-refresh")

    override suspend fun save(tokens: AuthTokens) {
        this.tokens = tokens
    }

    override suspend fun read(): AuthTokens? = tokens

    override suspend fun clear() {
        tokens = null
    }
}

private val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
