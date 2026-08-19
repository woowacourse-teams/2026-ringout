package com.joon.ringout.data.missionhistory

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

class KtorMissionHistoryRemoteDataSource(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
) : MissionHistoryRemoteDataSource {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage)

    override suspend fun hasAccessToken(): Boolean =
        tokenStorage.read()?.accessToken?.isNotBlank() == true

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.get(ApiConfig.url("/api/v1/stamp")) {
                bearerAuth(accessToken)
                parameter("year", month.year)
                parameter("month", month.month)
            }
        }
        val body = response.decodeOrThrow<MonthlyStampsResponse>()
        check(body.isSuccess) { body.message }
        val result = checkNotNull(body.result) { "월별 스탬프 응답이 비어 있어요." }
        check(result.year == month.year && result.month == month.month) {
            "요청한 월과 다른 스탬프 응답을 받았어요."
        }
        return result.successDates.map { date ->
            MissionHistoryDto(
                result = MissionResult.SUCCESS.persistedValue,
                completedAt = date,
            )
        }
    }

    override suspend fun recordSuccess(completedAt: MissionDate): Boolean {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.post(ApiConfig.url("/api/v1/stamp/success")) {
                bearerAuth(accessToken)
                parameter("completedAt", completedAt.iso8601)
            }
        }
        val body = response.decodeOrThrow<CreateStampResponse>()
        check(body.isSuccess) { body.message }
        val result = checkNotNull(body.result) { "스탬프 성공 기록 응답이 비어 있어요." }
        check(result.completedAt == completedAt.iso8601 && result.goalResult == MissionResult.SUCCESS.persistedValue) {
            "스탬프 성공 기록 응답이 올바르지 않아요."
        }
        return true
    }

    override suspend fun recordFailure(terminatedAt: MissionDate): Boolean {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.post(ApiConfig.url("/api/v1/stamp/give-ups")) {
                bearerAuth(accessToken)
                parameter("terminatedAt", terminatedAt.iso8601)
            }
        }
        val body = response.decodeOrThrow<CreateGiveUpResponse>()
        check(body.isSuccess) { body.message }
        val result = checkNotNull(body.result) { "스탬프 실패 기록 응답이 비어 있어요." }
        check(result.terminatedAt == terminatedAt.iso8601 && result.goalResult == MissionResult.FAILURE.persistedValue) {
            "스탬프 실패 기록 응답이 올바르지 않아요."
        }
        return true
    }
}

@Serializable
private data class MonthlyStampsResponse(
    val year: Int,
    val month: Int,
    val successDates: List<String>,
)

@Serializable
private data class CreateStampResponse(
    val completedAt: String,
    val goalResult: String,
)

@Serializable
private data class CreateGiveUpResponse(
    val terminatedAt: String,
    val goalResult: String,
)

private suspend inline fun <reified T> HttpResponse.decodeOrThrow(): ApiResponse<T> {
    val responseBody = bodyAsText()
    if (status.isSuccess()) {
        return ApiJson.decodeFromString(responseBody)
    }

    val errorResponse = runCatching {
        ApiJson.decodeFromString<ApiErrorResponse>(responseBody)
    }.getOrNull()
    throw ApiException(
        statusCode = status.value,
        code = errorResponse?.code,
        apiMessage = errorResponse?.message ?: status.description,
        result = errorResponse?.result,
    )
}
