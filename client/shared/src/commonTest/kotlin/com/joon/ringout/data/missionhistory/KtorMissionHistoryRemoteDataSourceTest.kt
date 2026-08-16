package com.joon.ringout.data.missionhistory

import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.MissionDate
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KtorMissionHistoryRemoteDataSourceTest {
    @Test
    fun `선택한 월과 access token으로 성공 스탬프 날짜를 조회한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/stamp", request.url.encodedPath)
            assertEquals("2026", request.url.parameters["year"])
            assertEquals("8", request.url.parameters["month"])
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "COMMON200",
                      "message": "성공입니다.",
                      "result": {
                        "year": 2026,
                        "month": 8,
                        "successDates": ["2026-08-01", "2026-08-13"]
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val dataSource = KtorMissionHistoryRemoteDataSource(client, FakeStampTokenStorage())

        val history = dataSource.getHistory(MissionYearMonth(2026, 8))

        assertEquals(listOf("2026-08-01", "2026-08-13"), history.map { it.completedAt })
        assertEquals(listOf("SUCCESS", "SUCCESS"), history.map { it.result })
        client.close()
    }

    @Test
    fun `미션 성공 날짜와 access token으로 스탬프 성공 기록을 저장한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/stamp/success", request.url.encodedPath)
            assertEquals("2026-08-13", request.url.parameters["completedAt"])
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "STAMP201",
                      "message": "목적지 도착 성공 결과가 저장되었습니다.",
                      "result": {
                        "completedAt": "2026-08-13",
                        "goalResult": "SUCCESS"
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val dataSource = KtorMissionHistoryRemoteDataSource(client, FakeStampTokenStorage())

        assertEquals(true, dataSource.recordSuccess(MissionDate.parse("2026-08-13")))
        client.close()
    }

    @Test
    fun `강제 종료 날짜와 access token으로 스탬프 실패 기록을 저장한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/v1/stamp/give-ups", request.url.encodedPath)
            assertEquals("2026-08-13", request.url.parameters["terminatedAt"])
            assertEquals("Bearer ringout-access", request.headers[HttpHeaders.Authorization])
            respond(
                content = """
                    {
                      "isSuccess": true,
                      "code": "STAMP201",
                      "message": "목표 실패가 기록되었습니다.",
                      "result": {
                        "terminatedAt": "2026-08-13",
                        "goalResult": "FAILURE"
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }) {
            configureRingoutHttpClient()
        }
        val dataSource = KtorMissionHistoryRemoteDataSource(client, FakeStampTokenStorage())

        assertEquals(true, dataSource.recordFailure(MissionDate.parse("2026-08-13")))
        client.close()
    }
}

private class FakeStampTokenStorage : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) = Unit

    override suspend fun read(): AuthTokens = AuthTokens(
        accessToken = "ringout-access",
        refreshToken = "ringout-refresh",
    )

    override suspend fun clear() = Unit
}
