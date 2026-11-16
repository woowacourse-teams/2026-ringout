package com.joon.ringout.data.forest

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceAccount
import com.joon.ringout.data.alarmoccurrence.checkSession
import com.joon.ringout.data.alarmoccurrence.checkToken
import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlin.time.Instant

internal class ForestOnboardingRemoteDataSource(
    private val client: HttpClient,
    tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
) {
    private val requests = AuthenticatedRequestExecutor(client, tokenStorage, authSession)

    suspend fun complete(account: AlarmOccurrenceAccount, completedAtEpochMillis: Long): Long {
        // Build once: authentication retry must submit the same first local completion time.
        val body = CompleteOnboardingRequest(Instant.fromEpochMilliseconds(completedAtEpochMillis).toString())
        val response = requests.execute { token ->
            account.checkToken(authSession, token)
            client.put(ApiConfig.url("/api/v1/forest/onboarding")) {
                bearerAuth(token)
                setBody(body)
            }
        }
        val text = response.bodyAsText()
        currentCoroutineContext().ensureActive()
        account.checkSession(authSession)
        if (response.status.value !in setOf(200, 201)) {
            val error = runCatching { ApiJson.decodeFromString<ApiErrorResponse>(text) }.getOrNull()
            throw ApiException(response.status.value, error?.code, error?.message ?: response.status.description)
        }
        val result = ApiJson.decodeFromString<ApiResponse<CompleteOnboardingResponse>>(text)
        check(result.isSuccess) { result.message }
        return Instant.parse(checkNotNull(result.result).onboardingCompletedAt).toEpochMilliseconds().also {
            require(it >= 0) { "온보딩 완료 시각이 올바르지 않습니다." }
        }
    }
}

@Serializable
private data class CompleteOnboardingRequest(val completedAt: String)

@Serializable
private data class CompleteOnboardingResponse(val onboardingCompletedAt: String)
