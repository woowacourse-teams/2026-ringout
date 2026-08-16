package com.joon.ringout.data.destination

import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.destination.SavedDestination
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KtorDestinationRemoteDataSourceTest {
    @Test
    fun `목적지 전체 조회 응답을 저장 목적지 목록으로 변환한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/destinations", request.url.encodedPath)
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "목적지를 조회했습니다.",
                      "result": [
                        {
                          "destinationId": 1,
                          "alias": "집",
                          "latitude": 37.4979,
                          "longitude": 127.0276
                        },
                        {
                          "destinationId": 2,
                          "alias": "회사",
                          "latitude": 37.5665,
                          "longitude": 126.978
                        }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val dataSource = KtorDestinationRemoteDataSource(client, FakeTokenStorage())

        val destinations = dataSource.fetchAll()

        assertEquals(listOf("집", "회사"), destinations.map(SavedDestination::name))
        assertEquals(127.0276, destinations.first().longitude)
        client.close()
    }

    @Test
    fun `목적지 저장 요청에 Ringout access token과 좌표를 전송한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/destinations", request.url.encodedPath)
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            val requestBody = (request.body as TextContent).text
            assertTrue(requestBody.contains("\"alias\":\"회사\""))
            assertTrue(requestBody.contains("\"latitude\":37.5665"))
            assertTrue(requestBody.contains("\"longitude\":126.978"))
            assertFalse(requestBody.contains("address"))
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION201",
                      "message": "목적지가 저장되었습니다.",
                      "result": { "destinationId": 42 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val dataSource = KtorDestinationRemoteDataSource(client, FakeTokenStorage())

        val saved = dataSource.create(
            SavedDestination(
                name = "회사",
                address = "서울특별시 중구 세종대로 110",
                latitude = 37.5665,
                longitude = 126.978,
            ),
        )

        assertEquals(42L, saved.id)
        assertEquals("서울특별시 중구 세종대로 110", saved.address)
        client.close()
    }

    @Test
    fun `목적지 삭제 요청에 destination id와 Ringout access token을 전송한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Delete, request.method)
            assertEquals("/api/v1/destinations/42", request.url.encodedPath)
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "삭제되었습니다.",
                      "result": null
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val dataSource = KtorDestinationRemoteDataSource(client, FakeTokenStorage())

        assertTrue(dataSource.delete(42L))
        client.close()
    }

    @Test
    fun `목적지 별명 수정 요청에 destination id와 alias를 전송한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Patch, request.method)
            assertEquals("/api/v1/destinations/42", request.url.encodedPath)
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            val requestBody = (request.body as TextContent).text
            assertEquals("{\"alias\":\"새 회사\"}", requestBody)
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "DESTINATION200",
                      "message": "수정되었습니다.",
                      "result": {
                        "destinationId": 42,
                        "alias": "새 회사",
                        "latitude": 37.5665,
                        "longitude": 126.978
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val dataSource = KtorDestinationRemoteDataSource(client, FakeTokenStorage())

        assertTrue(dataSource.updateName(id = 42L, name = "새 회사"))
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
