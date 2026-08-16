package com.joon.ringout.data.destination

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.destination.SavedDestination
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

class KtorDestinationRemoteDataSource(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
) : DestinationRemoteDataSource {
    override suspend fun create(destination: SavedDestination): SavedDestination {
        val accessToken = checkNotNull(tokenStorage.read()?.accessToken) {
            "로그인이 필요한 기능이에요."
        }
        val response = httpClient.post(ApiConfig.url("/api/v1/destinations")) {
            bearerAuth(accessToken)
            setBody(
                DestinationCreateRequest(
                    alias = destination.name,
                    latitude = destination.latitude,
                    longitude = destination.longitude,
                ),
            )
        }
        val body = response.decodeOrThrow<DestinationCreateResponse>()
        check(body.isSuccess) { body.message }
        val result = checkNotNull(body.result) { "목적지 저장 응답이 비어 있어요." }
        return destination.copy(id = result.destinationId)
    }
}

@Serializable
private data class DestinationCreateRequest(
    val alias: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class DestinationCreateResponse(
    val destinationId: Long,
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
