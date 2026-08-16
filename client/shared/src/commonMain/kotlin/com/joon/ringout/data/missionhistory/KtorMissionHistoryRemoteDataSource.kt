package com.joon.ringout.data.missionhistory

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

class KtorMissionHistoryRemoteDataSource(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
) : MissionHistoryRemoteDataSource {
    override suspend fun hasAccessToken(): Boolean =
        tokenStorage.read()?.accessToken?.isNotBlank() == true

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> {
        val accessToken = checkNotNull(tokenStorage.read()?.accessToken) {
            "로그인이 필요한 기능이에요."
        }
        val response = httpClient.get(ApiConfig.url("/api/v1/stamp")) {
            bearerAuth(accessToken)
            parameter("year", month.year)
            parameter("month", month.month)
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
}

@Serializable
private data class MonthlyStampsResponse(
    val year: Int,
    val month: Int,
    val successDates: List<String>,
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
