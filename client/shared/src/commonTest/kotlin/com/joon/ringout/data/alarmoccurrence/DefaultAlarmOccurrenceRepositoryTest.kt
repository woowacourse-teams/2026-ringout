package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceEvent
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRingingType
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceStart
import com.joon.ringout.domain.alarmoccurrence.AlarmRepeatRinging
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
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
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.time.Instant

class DefaultAlarmOccurrenceRepositoryTest {
    @Test
    fun `최초 울림은 인증된 POST로 예정 시각과 실제 시작 시각을 구분해 전송한다`() = runTest {
        val client = mockClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(Path, request.url.encodedPath)
            assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
            assertEquals(ContentType.Application.Json, request.body.contentType)
            assertEquals(json("alarmId" to "alarm-1", "scheduledAt" to ScheduledAt.toString(), "startedAt" to StartedAt.toString()), request.jsonBody())
            respondJson(Success, HttpStatusCode.Created)
        }

        val result = repository(client).start(Start)

        assertEquals(Id, result.id)
        assertEquals(StartedAt, result.startedAt)
        assertEquals(AlarmOccurrenceRingingType.INITIAL, result.ringings.single().type)
        assertEquals(StartedAt, result.ringings.single().ringingAt)
        assertNull(result.ringings.single().eventId)
        assertNull(result.ringings.single().dismissedAt)
        assertNull(result.arrivedAt)
        assertNull(result.forceEndedAt)
        client.close()
    }

    @Test
    fun `중복 생성의 200 응답도 기존 서버 실행으로 반환한다`() = runTest {
        val client = mockClient { respondJson(Success) }
        assertEquals(Id, repository(client).start(Start).id)
        client.close()
    }

    @Test
    fun `다섯 종류의 PATCH는 각 이벤트에 필요한 필드만 전송한다`() = runTest {
        val ringing = AlarmRepeatRinging("repeat-1", RepeatedAt)
        val cases = listOf(
            AlarmOccurrenceEvent.InitialDismissed(DismissedAt) to json("dismissedAt" to DismissedAt.toString()),
            AlarmOccurrenceEvent.RepeatRang(ringing) to json("eventId" to "repeat-1", "ringingAt" to RepeatedAt.toString()),
            AlarmOccurrenceEvent.RepeatDismissed(ringing, DismissedAt) to json(
                "eventId" to "repeat-1", "ringingAt" to RepeatedAt.toString(), "dismissedAt" to DismissedAt.toString(),
            ),
            AlarmOccurrenceEvent.Arrived(EndedAt) to json("arrivedAt" to EndedAt.toString()),
            AlarmOccurrenceEvent.ForceEnded(EndedAt) to json("forceEndedAt" to EndedAt.toString()),
        )
        for ((event, expected) in cases) {
            val client = mockClient { request ->
                assertEquals(HttpMethod.Patch, request.method)
                assertEquals("$Path/${Id.value}", request.url.encodedPath)
                assertEquals("Bearer access", request.headers[HttpHeaders.Authorization])
                assertEquals(expected, request.jsonBody())
                respondJson(Success)
            }
            assertEquals(Id, repository(client).recordEvent(Id, event).id)
            client.close()
        }
    }

    @Test
    fun `응답의 재울림과 도착 및 강제 종료 시각을 Domain으로 변환한다`() = runTest {
        for (terminalField in listOf("arrivedAt", "forceEndedAt")) {
            val response = """{"isSuccess":true,"code":"OK","message":"OK","result":{
                "alarmOccurrenceId":"${Id.value}","startedAt":"2026-10-01T07:00:02+09:00",
                "ringings":[
                    {"type":"INITIAL","ringingAt":"2026-10-01T07:00:02+09:00","dismissedAt":"2026-10-01T07:01:00+09:00"},
                    {"type":"REPEAT","eventId":"repeat-1","ringingAt":"2026-10-01T07:10:00+09:00","dismissedAt":"2026-10-01T07:11:00+09:00"}
                ],"$terminalField":"2026-10-01T07:30:00+09:00","futureField":true}}"""
            val client = mockClient { respondJson(response) }
            val event = if (terminalField == "arrivedAt") AlarmOccurrenceEvent.Arrived(EndedAt) else AlarmOccurrenceEvent.ForceEnded(EndedAt)
            val result = repository(client).recordEvent(Id, event)
            assertEquals(listOf(AlarmOccurrenceRingingType.INITIAL, AlarmOccurrenceRingingType.REPEAT), result.ringings.map { it.type })
            assertEquals("repeat-1", result.ringings.last().eventId)
            assertEquals(RepeatedAt, result.ringings.last().ringingAt)
            assertEquals(DismissedAt, result.ringings.last().dismissedAt)
            assertEquals(if (terminalField == "arrivedAt") EndedAt else null, result.arrivedAt)
            assertEquals(if (terminalField == "forceEndedAt") EndedAt else null, result.forceEndedAt)
            client.close()
        }
    }

    @Test
    fun `생성과 갱신의 인증 재시도는 원래 식별자와 발생 시각을 그대로 보낸다`() = runTest {
        for (isStart in listOf(true, false)) {
            val bodies = mutableListOf<JsonObject>()
            var refreshCount = 0
            val storage = TestTokenStorage()
            val client = mockClient { request ->
                if (request.url.encodedPath == "/api/v1/auth/reissue") {
                    refreshCount++
                    respondJson("""{"isSuccess":true,"code":"AUTH200","message":"OK","result":{"accessToken":"renewed","refreshToken":"refresh"}}""")
                } else {
                    bodies += request.jsonBody()
                    if (request.headers[HttpHeaders.Authorization] == "Bearer access") {
                        respondJson("""{"isSuccess":false,"code":"AUTH401","message":"expired"}""", HttpStatusCode.Unauthorized)
                    } else {
                        assertEquals("Bearer renewed", request.headers[HttpHeaders.Authorization])
                        respondJson(Success)
                    }
                }
            }
            val repo = repository(client, storage)
            if (isStart) repo.start(Start) else repo.recordEvent(Id, AlarmOccurrenceEvent.RepeatDismissed(AlarmRepeatRinging("repeat-1", RepeatedAt), DismissedAt))
            assertEquals(1, refreshCount)
            assertEquals(2, bodies.size)
            assertEquals(bodies.first(), bodies.last())
            client.close()
        }
    }

    @Test
    fun `오류 상태와 서버 코드 및 메시지를 호출자에게 전달한다`() = runTest {
        for (status in listOf(400, 403, 404, 409, 500)) {
            var requestCount = 0
            val client = mockClient {
                requestCount++
                respondJson("""{"isSuccess":false,"code":"ALARM_OCCURRENCE$status","message":"처리 실패","result":{"reason":"conflict"}}""", HttpStatusCode.fromValue(status))
            }
            val error = assertFailsWith<ApiException> { repository(client).recordEvent(Id, AlarmOccurrenceEvent.Arrived(EndedAt)) }
            assertEquals(status, error.statusCode)
            assertEquals("ALARM_OCCURRENCE$status", error.code)
            assertEquals("처리 실패", error.apiMessage)
            assertEquals(json("reason" to "conflict"), error.result)
            assertEquals(1, requestCount)
            client.close()
        }
    }

    @Test
    fun `JSON이 아닌 서버 오류도 상태 코드를 보존한다`() = runTest {
        val client = mockClient { respond("upstream unavailable", HttpStatusCode.BadGateway) }
        val error = assertFailsWith<ApiException> { repository(client).start(Start) }
        assertEquals(502, error.statusCode)
        assertNull(error.code)
        client.close()
    }

    @Test
    fun `HTTP 성공이어도 실패 표시나 결과 누락을 성공으로 처리하지 않는다`() = runTest {
        val failed = mockClient { respondJson("""{"isSuccess":false,"code":"FAILED","message":"실패"}""") }
        assertEquals("FAILED", assertFailsWith<ApiException> { repository(failed).start(Start) }.code)
        failed.close()
        val empty = mockClient { respondJson("""{"isSuccess":true,"code":"OK","message":"OK","result":null}""") }
        assertFailsWith<IllegalStateException> { repository(empty).start(Start) }
        empty.close()
    }

    @Test
    fun `시작 시각이 없는 성공 응답을 임의의 시각으로 보완하지 않는다`() = runTest {
        val client = mockClient { respondJson(Success.replace("\"startedAt\":\"2026-10-01T07:00:02+09:00\"", "\"startedAt\":null")) }
        assertFailsWith<SerializationException> { repository(client).start(Start) }
        client.close()
    }

    @Test
    fun `토큰이 없으면 서버에 요청하지 않는다`() = runTest {
        var requests = 0
        val client = mockClient { requests++; respondJson(Success) }
        assertFailsWith<IllegalStateException> { repository(client, TestTokenStorage(null)).start(Start) }
        assertEquals(0, requests)
        client.close()
    }

    @Test
    fun `요청 취소를 다른 오류로 바꾸지 않는다`() = runTest {
        val cancellation = CancellationException("취소")
        val client = mockClient { throw cancellation }
        assertFailsWith<CancellationException> { repository(client).start(Start) }
        client.close()
    }
}

private const val Path = "/api/v1/alarm-occurrences"
private val Id = AlarmOccurrenceId("5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f")
private val ScheduledAt = Instant.parse("2026-10-01T07:00:00+09:00")
private val StartedAt = Instant.parse("2026-10-01T07:00:02+09:00")
private val RepeatedAt = Instant.parse("2026-10-01T07:10:00+09:00")
private val DismissedAt = Instant.parse("2026-10-01T07:11:00+09:00")
private val EndedAt = Instant.parse("2026-10-01T07:30:00+09:00")
private val Start = AlarmOccurrenceStart("alarm-1", ScheduledAt, StartedAt)
private val Success = """{"isSuccess":true,"code":"ALARM_OCCURRENCE200","message":"OK","result":{
    "alarmOccurrenceId":"${Id.value}","startedAt":"2026-10-01T07:00:02+09:00",
    "ringings":[{"type":"INITIAL","eventId":null,"ringingAt":"2026-10-01T07:00:02+09:00","dismissedAt":null}],
    "arrivedAt":null,"forceEndedAt":null}}"""

private fun json(vararg fields: Pair<String, String>) = JsonObject(fields.associate { it.first to JsonPrimitive(it.second) })
private fun HttpRequestData.jsonBody() = ApiJson.parseToJsonElement((body as TextContent).text).jsonObject
private fun MockRequestHandleScope.respondJson(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
    HttpClient(MockEngine(handler)) { configureRingoutHttpClient() }
private fun repository(client: HttpClient, storage: SecureTokenStorage = TestTokenStorage()) =
    DefaultAlarmOccurrenceRepository(client, storage, AuthSession().apply { markAuthenticated() })

private class TestTokenStorage(var tokens: AuthTokens? = AuthTokens("access", "refresh")) : SecureTokenStorage {
    override suspend fun read() = tokens
    override suspend fun save(tokens: AuthTokens) { this.tokens = tokens }
    override suspend fun clear() { tokens = null }
}
