package com.joon.ringout.data.destination

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
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KtorDestinationRemoteDataSourceTest {
    @Test
    fun `목적지 배열과 숫자 ID 응답을 서버 목적지 목록으로 변환한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/destinations", request.url.encodedPath)
            assertEquals("Bearer ${accessToken()}", request.headers[HttpHeaders.Authorization])
            respondJson(
                """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "조회되었습니다.",
                      "result": [
                        {
                          "destinationId": 101,
                          "alias": "집",
                          "latitude": 37.4979,
                          "longitude": 127.0276
                        }
                      ]
                    }
                """.trimIndent(),
            )
        }) { configureRingoutHttpClient() }
        try {
            val destinations = KtorDestinationRemoteDataSource(client, FakeTokenStorage()).fetchAll("7")

            assertEquals(
                listOf(RemoteSavedDestination("101", "집", 37.4979, 127.0276, 1)),
                destinations,
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `빈 목적지 배열은 정상적인 빈 목록으로 반환한다`() = runTest {
        val client = jsonClient("""
            {
              "isSuccess": true,
              "code": "DESTINATION200",
              "message": "조회되었습니다.",
              "result": []
            }
        """.trimIndent())
        try {
            assertTrue(KtorDestinationRemoteDataSource(client, FakeTokenStorage()).fetchAll("7").isEmpty())
        } finally {
            client.close()
        }
    }

    @Test
    fun `목록 result가 없으면 조회 실패로 처리한다`() = runTest {
        val client = jsonClient("""
            { "isSuccess": true, "code": "DESTINATION200", "message": "조회되었습니다.", "result": null }
        """.trimIndent())
        try {
            assertFailsWith<IllegalStateException> {
                KtorDestinationRemoteDataSource(client, FakeTokenStorage()).fetchAll("7")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `숫자 ID가 없는 저장은 POST로 생성하고 서버 ID를 반환한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/destinations", request.url.encodedPath)
            assertEquals("Bearer ${accessToken()}", request.headers[HttpHeaders.Authorization])
            val requestBody = (request.body as TextContent).text
            assertTrue(requestBody.contains("\"alias\":\"회사\""))
            assertTrue(requestBody.contains("\"latitude\":37.5665"))
            assertTrue(requestBody.contains("\"longitude\":126.978"))
            assertTrue(!requestBody.contains("destinationVersion"))
            respondJson(
                """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION201",
                      "message": "목적지가 생성되었습니다.",
                      "result": { "destinationId": 102 }
                    }
                """.trimIndent(),
                HttpStatusCode.Created,
            )
        }) { configureRingoutHttpClient() }
        try {
            val destination = RemoteSavedDestination("local-uuid", "회사", 37.5665, 126.978, 1)

            assertEquals(
                destination.copy(id = "102"),
                KtorDestinationRemoteDataSource(client, FakeTokenStorage()).create("7", destination),
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun `삭제 성공은 DESTINATION200과 result 없는 응답을 허용한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/v1/destinations/101", request.url.encodedPath)
            assertEquals("Bearer ${accessToken()}", request.headers[HttpHeaders.Authorization])
            respondJson(
                """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "삭제되었습니다."
                    }
                """.trimIndent(),
            )
        }) { configureRingoutHttpClient() }
        try {
            KtorDestinationRemoteDataSource(client, FakeTokenStorage()).delete("7", "101")
        } finally {
            client.close()
        }
    }

    @Test
    fun `다른 사용자 목적지 삭제 403은 성공으로 처리하지 않는다`() = runTest {
        val client = HttpClient(MockEngine {
            respondJson(
                """
                    {
                      "isSuccess": false,
                      "code": "DESTINATION403",
                      "message": "삭제할 수 없는 목적지입니다.",
                      "result": null
                    }
                """.trimIndent(),
                HttpStatusCode.Forbidden,
            )
        }) { configureRingoutHttpClient() }
        try {
            val error = assertFailsWith<ApiException> {
                KtorDestinationRemoteDataSource(client, FakeTokenStorage()).delete("7", "101")
            }
            assertEquals(403, error.statusCode)
            assertEquals("DESTINATION403", error.code)
        } finally {
            client.close()
        }
    }

    @Test
    fun `삭제 응답의 비어 있지 않은 result는 실패로 처리한다`() = runTest {
        val client = jsonClient("""
            {
              "isSuccess": true,
              "code": "DESTINATION200",
              "message": "삭제되었습니다.",
              "result": { "deleted": true }
            }
        """.trimIndent())
        try {
            assertFailsWith<IllegalStateException> {
                KtorDestinationRemoteDataSource(client, FakeTokenStorage()).delete("7", "101")
            }
        } finally {
            client.close()
        }
    }

    @Test
    fun `삭제 응답에서 result가 빠져도 성공으로 처리한다`() = runTest {
        val client = jsonClient("""
            {
              "isSuccess": true,
              "code": "DESTINATION200",
              "message": "삭제되었습니다."
            }
        """.trimIndent())
        try {
            KtorDestinationRemoteDataSource(client, FakeTokenStorage()).delete("7", "101")
        } finally {
            client.close()
        }
    }
}

private fun jsonClient(body: String): HttpClient = HttpClient(MockEngine { respondJson(body) }) {
    configureRingoutHttpClient()
}

private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

private fun accessToken(): String {
    val payload = Base64.UrlSafe.encode(
        "{\"tokenType\":\"ACCESS\",\"userId\":7,\"sub\":\"7\"}".encodeToByteArray(),
    ).trimEnd('=')
    return "eyJhbGciOiJub25lIn0.$payload."
}

private class FakeTokenStorage : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) = Unit

    override suspend fun read(): AuthTokens = AuthTokens(
        accessToken = accessToken(),
        refreshToken = "ringout-refresh",
        userId = 7,
    )

    override suspend fun clear() = Unit
}
