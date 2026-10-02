package com.joon.ringout.data.alarmmovement

import com.joon.ringout.data.alarmoccurrence.captureAlarmOccurrenceAccount
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.alarmmovement.AlarmMovementAction
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultAlarmMovementRepositoryTest {
    @Test
    fun `세 가지 이동 행동은 서버 실행 UUID를 새 필드 이름으로 전송한다`() = runTest {
        for ((action, status) in listOf(
            AlarmMovementAction.START_MOVEMENT to "MOVEMENT_STARTED",
            AlarmMovementAction.ARRIVE to "ARRIVED", AlarmMovementAction.GIVE_UP to "GAVE_UP",
        )) {
            val client = HttpClient(MockEngine { request ->
                assertEquals(HttpMethod.Post, request.method)
                assertEquals("/api/v1/rooms/7/movements", request.url.encodedPath)
                assertEquals("Bearer ${token(1)}", request.headers[HttpHeaders.Authorization])
                assertEquals(JsonObject(mapOf("alarmOccurrenceId" to JsonPrimitive(Id.value), "action" to JsonPrimitive(action.name))),
                    ApiJson.parseToJsonElement((request.body as TextContent).text))
                json("""{"isSuccess":true,"code":"MOVEMENT200","message":"OK","result":{"status":"$status"}}""")
            }) { configureRingoutHttpClient() }
            try { repository(client).changeMovement(7, Id, action) } finally { client.close() }
        }
    }

    @Test
    fun `인증된 모임 목록에서 현재 가입한 모임만 중복 없이 선택한다`() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/v1/rooms", request.url.encodedPath)
            assertEquals("Bearer ${token(1)}", request.headers[HttpHeaders.Authorization])
            json("""{"isSuccess":true,"code":"ROOM200","message":"OK","result":{"rooms":[
                {"roomId":7,"isJoined":true},{"roomId":8,"isJoined":false},{"roomId":7,"isJoined":true}]}}""")
        }) { configureRingoutHttpClient() }
        try { assertEquals(listOf(7L), repository(client).getJoinedRoomIds()) } finally { client.close() }
    }

    @Test
    fun `동일한 행동이 이미 적용된 충돌만 완료로 인정한다`() = runTest {
        for ((action, message) in listOf(
            AlarmMovementAction.START_MOVEMENT to "이미 이동을 시작한 상태입니다.",
            AlarmMovementAction.ARRIVE to "이미 목적지에 도착한 알람입니다.",
            AlarmMovementAction.GIVE_UP to "이미 이동을 포기한 알람입니다.",
        )) {
            val client = HttpClient(MockEngine {
                json("""{"isSuccess":false,"code":"MOVEMENT409","message":"$message"}""", HttpStatusCode.Conflict)
            }) { configureRingoutHttpClient() }
            try {
                val repo = repository(client)
                repo.changeMovement(7, Id, action)
                val other = if (action == AlarmMovementAction.ARRIVE) AlarmMovementAction.GIVE_UP else AlarmMovementAction.ARRIVE
                assertEquals(409, assertFailsWith<ApiException> { repo.changeMovement(7, Id, other) }.statusCode)
            } finally { client.close() }
        }
    }

    @Test
    fun `이동 기록 누락과 권한 오류는 상태 코드 그대로 전달한다`() = runTest {
        for (status in listOf(400, 403, 404, 500)) {
            val client = HttpClient(MockEngine {
                json("""{"isSuccess":false,"code":"MOVEMENT$status","message":"실패"}""", HttpStatusCode.fromValue(status))
            }) { configureRingoutHttpClient() }
            try {
                assertEquals(status, assertFailsWith<ApiException> {
                    repository(client).changeMovement(7, Id, AlarmMovementAction.ARRIVE)
                }.statusCode)
            } finally { client.close() }
        }
    }

    @Test
    fun `응답이 요청한 이동 상태와 다르면 성공으로 처리하지 않는다`() = runTest {
        val client = HttpClient(MockEngine {
            json("""{"isSuccess":true,"code":"MOVEMENT200","message":"OK","result":{"status":"GAVE_UP"}}""")
        }) { configureRingoutHttpClient() }
        try {
            assertFailsWith<IllegalStateException> { repository(client).changeMovement(7, Id, AlarmMovementAction.ARRIVE) }
        } finally { client.close() }
    }

    @Test
    fun `인증 갱신 후에도 같은 실행 UUID와 행동으로 재전송한다`() = runTest {
        val tokens = Tokens()
        val bodies = mutableListOf<String>()
        val client = HttpClient(MockEngine { request ->
            if (request.url.encodedPath == "/api/v1/auth/reissue") {
                json("""{"isSuccess":true,"code":"AUTH200","message":"OK","result":{"accessToken":"${token(1, "renewed")}","refreshToken":"refresh"}}""")
            } else {
                bodies += (request.body as TextContent).text
                if (bodies.size == 1) json("""{"isSuccess":false,"code":"AUTH401","message":"expired"}""", HttpStatusCode.Unauthorized)
                else json("""{"isSuccess":true,"code":"MOVEMENT200","message":"OK","result":{"status":"ARRIVED"}}""")
            }
        }) { configureRingoutHttpClient() }
        try {
            repository(client, tokens).changeMovement(7, Id, AlarmMovementAction.ARRIVE)
            assertEquals(2, bodies.size)
            assertEquals(bodies.first(), bodies.last())
        } finally { client.close() }
    }

    @Test
    fun `계정이 바뀌면 새 계정 토큰으로 이전 이동을 전송하지 않는다`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { calls++; error("전송하면 안 됩니다.") }) { configureRingoutHttpClient() }
        val tokens = Tokens()
        val session = AuthSession().apply { startNewSession() }
        val repo = repository(client, tokens, session)
        tokens.save(AuthTokens(token(2), "refresh"))
        try {
            assertFailsWith<CancellationException> { repo.changeMovement(7, Id, AlarmMovementAction.ARRIVE) }
            assertEquals(0, calls)
        } finally { client.close() }
    }
}

private suspend fun repository(client: HttpClient, tokens: Tokens = Tokens(),
    session: AuthSession = AuthSession().apply { startNewSession() }) =
    DefaultAlarmMovementRepository(client, tokens, session, checkNotNull(captureAlarmOccurrenceAccount(tokens, session)))

private class Tokens : SecureTokenStorage {
    private var value: AuthTokens? = AuthTokens(token(1), "refresh")
    override suspend fun read() = value
    override suspend fun save(tokens: AuthTokens) { value = tokens }
    override suspend fun clear() { value = null }
}
private fun token(id: Int, signature: String = "signature") = "header." + Base64.UrlSafe.encode(
    """{"sub":"$id","userId":$id,"tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=') + ".$signature"
private val Id = AlarmOccurrenceId("5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f")
private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
