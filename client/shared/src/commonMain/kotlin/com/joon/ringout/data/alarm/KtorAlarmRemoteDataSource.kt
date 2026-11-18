package com.joon.ringout.data.alarm

import com.joon.ringout.data.alarmoccurrence.alarmOccurrenceTokenOwner
import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement

class KtorAlarmRemoteDataSource(
    private val httpClient: HttpClient,
    tokenStorage: SecureTokenStorage,
) : AlarmRemoteDataSource {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage)

    override suspend fun fetchAll(ownerAccountId: String): List<RemoteAlarm> {
        val response = execute(ownerAccountId) { accessToken ->
            httpClient.get(ApiConfig.url(AlarmsPath)) { bearerAuth(accessToken) }
        }
        val alarms = checkNotNull(response.decodeResult<AlarmListResponse>()) {
            "알람 목록 응답이 비어 있어요."
        }.alarms
        check(alarms.distinctBy(RemoteAlarm::alarmId).size == alarms.size) {
            "알람 목록에 중복 ID가 있어요."
        }
        return alarms.map { it.validatedResponse().normalized() }
    }

    override suspend fun upsert(ownerAccountId: String, alarm: RemoteAlarm): RemoteAlarm {
        val url = alarmUrl(alarm.alarmId)
        val request = AlarmWriteRequest(alarm.normalized())
        val response = execute(ownerAccountId) { accessToken ->
            httpClient.put(url) {
                bearerAuth(accessToken)
                setBody(request)
            }
        }
        val saved = checkNotNull(response.decodeResult<RemoteAlarm>()) {
            "알람 저장 응답이 비어 있어요."
        }.validatedResponse()
        check(saved.alarmId == alarm.alarmId && saved.alarmVersion >= alarm.alarmVersion) {
            "알람 저장 응답의 ID 또는 버전이 일치하지 않아요."
        }
        return saved.normalized()
    }

    override suspend fun delete(ownerAccountId: String, alarmId: String) {
        val url = alarmUrl(alarmId)
        val response = execute(ownerAccountId) { accessToken ->
            httpClient.delete(url) { bearerAuth(accessToken) }
        }
        check(response.decodeResult<JsonElement>() == null) {
            "알람 삭제 응답은 result를 포함하지 않아야 해요."
        }
    }

    private suspend fun execute(
        ownerAccountId: String,
        request: suspend (accessToken: String) -> HttpResponse,
    ): HttpResponse = authenticatedRequests.execute { accessToken ->
        check(alarmOccurrenceTokenOwner(accessToken) == ownerAccountId) {
            "현재 로그인 계정이 알람 계정과 일치하지 않아요."
        }
        request(accessToken)
    }

    private fun alarmUrl(alarmId: String): String {
        require(validAlarmId(alarmId)) { "알람 ID는 공백 없이 1~36자여야 해요." }
        return URLBuilder(ApiConfig.url(AlarmsPath))
            .apply { appendPathSegments(alarmId, encodeSlash = true) }
            .buildString()
    }
}

@Serializable
private data class AlarmListResponse(val alarms: List<RemoteAlarm>)

@Serializable
private data class AlarmWriteRequest(
    val time: String,
    val dayOfWeek: List<RemoteAlarmDay>,
    val repeatTime: Int,
    val destinationName: String,
    val destinationLatitude: Double,
    val destinationLongitude: Double,
    val alarmVersion: Long,
) {
    constructor(alarm: RemoteAlarm) : this(
        time = alarm.time,
        dayOfWeek = alarm.dayOfWeek,
        repeatTime = alarm.repeatTime,
        destinationName = alarm.destinationName,
        destinationLatitude = alarm.destinationLatitude,
        destinationLongitude = alarm.destinationLongitude,
        alarmVersion = alarm.alarmVersion,
    )
}

private fun validAlarmId(alarmId: String): Boolean =
    alarmId.length in 1..36 && alarmId.none(Char::isWhitespace)

private fun RemoteAlarm.validatedResponse(): RemoteAlarm = apply {
    check(
        validAlarmId(alarmId) &&
            AlarmTimePattern.matches(time) &&
            dayOfWeek.distinct().size == dayOfWeek.size &&
            repeatTime in 0..30 &&
            destinationName.trim().length in 1..20 &&
            destinationLatitude in -90.0..90.0 &&
            destinationLongitude in -180.0..180.0,
    ) { "알람 응답의 설정이 유효하지 않아요." }
}

private suspend inline fun <reified T> HttpResponse.decodeResult(): T? {
    val responseBody = bodyAsText()
    if (status != HttpStatusCode.OK) {
        val error = try {
            ApiJson.decodeFromString<ApiErrorResponse>(responseBody)
        } catch (_: SerializationException) {
            null
        }
        throw ApiException(
            statusCode = status.value,
            code = error?.code,
            apiMessage = error?.message ?: status.description,
            result = error?.result,
        )
    }
    val body = ApiJson.decodeFromString<ApiResponse<T>>(responseBody)
    check(body.isSuccess && body.code == AlarmSuccessCode) { body.message }
    return body.result
}

private val AlarmTimePattern = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
private const val AlarmsPath = "/api/v1/alarms"
private const val AlarmSuccessCode = "ALARM200"
