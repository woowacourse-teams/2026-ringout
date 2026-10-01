package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrence
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceEvent
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceId
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceRepository
import com.joon.ringout.domain.alarmoccurrence.AlarmOccurrenceStart
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.auth.getAuthSession
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromString

class DefaultAlarmOccurrenceRepository(
    private val httpClient: HttpClient,
    tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession = getAuthSession(),
    private val account: AlarmOccurrenceAccount? = null,
) : AlarmOccurrenceRepository {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage, authSession)

    override suspend fun start(start: AlarmOccurrenceStart): AlarmOccurrence {
        val request = start.toRequest()
        return authenticatedRequests.execute { accessToken ->
            account?.checkToken(authSession, accessToken)
            httpClient.post(ApiConfig.url("/api/v1/alarm-occurrences")) {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.decodeOccurrence()
    }

    override suspend fun recordEvent(
        occurrenceId: AlarmOccurrenceId,
        event: AlarmOccurrenceEvent,
    ): AlarmOccurrence {
        val request = event.toRequest()
        return authenticatedRequests.execute { accessToken ->
            account?.checkToken(authSession, accessToken)
            httpClient.patch(ApiConfig.url("/api/v1/alarm-occurrences/${occurrenceId.value}")) {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.decodeOccurrence()
    }
}

private suspend fun HttpResponse.decodeOccurrence(): AlarmOccurrence {
    val responseBody = bodyAsText()
    if (!status.isSuccess()) {
        val error = runCatching { ApiJson.decodeFromString<ApiErrorResponse>(responseBody) }.getOrNull()
        throw ApiException(status.value, error?.code, error?.message ?: status.description, error?.result)
    }
    val body = ApiJson.decodeFromString<ApiResponse<AlarmOccurrenceDetailResponse>>(responseBody)
    if (!body.isSuccess) throw ApiException(status.value, body.code, body.message)
    return checkNotNull(body.result) { "알람 실행 응답이 비어 있어요." }.toDomain()
}
