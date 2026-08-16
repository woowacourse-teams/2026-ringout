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
import io.ktor.client.request.get
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
    override suspend fun fetchAll(): List<SavedDestination> {
        val accessToken = requireAccessToken()
        val response = httpClient.get(ApiConfig.url("/api/v1/destinations")) {
            bearerAuth(accessToken)
        }
        val body = response.decodeOrThrow<List<DestinationResponse>>()
        check(body.isSuccess) { body.message }
        return body.result.orEmpty().map(DestinationResponse::toDomain)
    }

    override suspend fun create(destination: SavedDestination): SavedDestination {
        val accessToken = requireAccessToken()
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

    private suspend fun requireAccessToken(): String =
        checkNotNull(tokenStorage.read()?.accessToken) {
            "로그인이 필요한 기능이에요."
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

@Serializable
private data class DestinationResponse(
    val destinationId: Long,
    val alias: String,
    val latitude: Double,
    val longitude: Double,
)

private fun DestinationResponse.toDomain(): SavedDestination = SavedDestination(
    id = destinationId,
    name = alias,
    address = "",
    latitude = latitude,
    longitude = longitude,
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
