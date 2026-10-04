package com.joon.ringout.data.alarmmovement

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceAccount
import com.joon.ringout.data.alarmoccurrence.checkSession
import com.joon.ringout.data.alarmoccurrence.checkToken
import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.alarmmovement.AlarmMovementAction
import com.joon.ringout.domain.alarmmovement.AlarmMovementRepository
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString

internal class DefaultAlarmMovementRepository(
    private val client: HttpClient,
    tokens: SecureTokenStorage,
    private val session: AuthSession,
    private val account: AlarmOccurrenceAccount,
) : AlarmMovementRepository {
    private val requests = AuthenticatedRequestExecutor(client, tokens, session)

    override suspend fun getJoinedRoomIds(): List<Long> {
        val response = requests.execute { token ->
            account.checkToken(session, token)
            client.get(ApiConfig.url("/api/v1/rooms")) { bearerAuth(token) }
        }
        account.checkSession(session)
        val body = response.decode<MovementRoomsResponse>("ROOM200")
        return body.rooms.filter { it.isJoined }.map { it.roomId.also { id -> require(id > 0) } }.distinct().sorted()
    }

    override suspend fun changeMovement(roomId: Long, occurrenceId: AlarmOccurrenceId, action: AlarmMovementAction) {
        require(roomId > 0)
        try {
            val response = requests.execute { token ->
                account.checkToken(session, token)
                client.post(ApiConfig.url("/api/v1/rooms/$roomId/movements")) {
                    bearerAuth(token)
                    contentType(ContentType.Application.Json)
                    setBody(MovementRequest(occurrenceId.value, action))
                }
            }
            account.checkSession(session)
            val status = response.decode<MovementResponse>("MOVEMENT200").status
            check(status == action.resultStatus()) { "이동 상태 응답이 요청한 행동과 다릅니다." }
        } catch (error: ApiException) {
            account.checkSession(session)
            // 응답 유실 후 재전송 또는 여러 모임에서 같은 실행을 처리한 경우만 완료로 인정한다.
            // 다른 종료 결과와의 충돌은 성공으로 숨기지 않는다.
            if (error.statusCode != 409 || error.code != "MOVEMENT409" || error.apiMessage != action.alreadyAppliedMessage()) throw error
        }
    }
}

@Serializable
private data class MovementRequest(val alarmOccurrenceId: String, val action: AlarmMovementAction)
@Serializable
private data class MovementResponse(val status: String)
@Serializable
private data class MovementRoomsResponse(val rooms: List<MovementRoom>)
@Serializable
private data class MovementRoom(val roomId: Long, val isJoined: Boolean)

private fun AlarmMovementAction.resultStatus() = when (this) {
    AlarmMovementAction.START_MOVEMENT -> "MOVEMENT_STARTED"
    AlarmMovementAction.ARRIVE -> "ARRIVED"
    AlarmMovementAction.GIVE_UP -> "GAVE_UP"
}
private fun AlarmMovementAction.alreadyAppliedMessage() = when (this) {
    AlarmMovementAction.START_MOVEMENT -> "이미 이동을 시작한 상태입니다."
    AlarmMovementAction.ARRIVE -> "이미 목적지에 도착한 알람입니다."
    AlarmMovementAction.GIVE_UP -> "이미 이동을 포기한 알람입니다."
}

private suspend inline fun <reified T> HttpResponse.decode(expectedCode: String): T {
    val raw = bodyAsText()
    if (!status.isSuccess()) {
        val error = try { ApiJson.decodeFromString<ApiErrorResponse>(raw) } catch (_: SerializationException) { null }
        throw ApiException(status.value, error?.code, error?.message ?: status.description, error?.result)
    }
    val body = ApiJson.decodeFromString<ApiResponse<T>>(raw)
    if (!body.isSuccess) throw ApiException(status.value, body.code, body.message)
    check(status == HttpStatusCode.OK && body.code == expectedCode) { "이동 API 응답이 올바르지 않습니다." }
    return checkNotNull(body.result) { "이동 API 응답이 비어 있습니다." }
}
