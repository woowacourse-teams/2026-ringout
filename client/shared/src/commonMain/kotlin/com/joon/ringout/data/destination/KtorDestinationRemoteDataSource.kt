package com.joon.ringout.data.destination

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.destination.SavedDestination
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement

class KtorDestinationRemoteDataSource(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
) : DestinationRemoteDataSource {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage)

    override suspend fun hasAccessToken(): Boolean =
        tokenStorage.read()?.accessToken?.isNotBlank() == true

    override suspend fun fetchAll(): List<SavedDestination> {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.get(ApiConfig.url("/api/v1/destinations")) {
                bearerAuth(accessToken)
            }
        }
        val body = response.decodeOrThrow<List<DestinationResponse>>()
        check(body.isSuccess) { body.message }
        return body.result.orEmpty().map(DestinationResponse::toDomain)
    }

    override suspend fun sync(destinations: List<SavedDestination>): List<SavedDestination> {
        val destinationsByClientId = destinations.associateBy(SavedDestination::id)
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.post(ApiConfig.url("/api/v1/destinations/sync")) {
                bearerAuth(accessToken)
                setBody(
                    DestinationSyncRequest(
                        destinations = destinations.map { destination ->
                            require(destination.id > 0L) {
                                "동기화할 목적지의 기기 ID가 올바르지 않아요."
                            }
                            DestinationSyncItemRequest(
                                clientDestinationId = destination.id,
                                alias = destination.name,
                                latitude = destination.latitude,
                                longitude = destination.longitude,
                            )
                        },
                    ),
                )
            }
        }
        val body = response.decodeOrThrow<DestinationSyncResponse>()
        check(body.isSuccess) { body.message }
        val result = checkNotNull(body.result) { "목적지 동기화 응답이 비어 있어요." }
        return result.destinations.map { synced ->
            SavedDestination(
                id = synced.destinationId,
                name = synced.alias,
                address = destinationsByClientId[synced.clientDestinationId]?.address.orEmpty(),
                latitude = synced.latitude,
                longitude = synced.longitude,
            )
        }
    }

    override suspend fun create(destination: SavedDestination): SavedDestination {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.post(ApiConfig.url("/api/v1/destinations")) {
                bearerAuth(accessToken)
                setBody(
                    DestinationCreateRequest(
                        alias = destination.name,
                        latitude = destination.latitude,
                        longitude = destination.longitude,
                    ),
                )
            }
        }
        val body = response.decodeOrThrow<DestinationCreateResponse>()
        check(body.isSuccess) { body.message }
        val result = checkNotNull(body.result) { "목적지 저장 응답이 비어 있어요." }
        return destination.copy(id = result.destinationId)
    }

    override suspend fun delete(id: Long): Boolean {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.delete(ApiConfig.url("/api/v1/destinations/$id")) {
                bearerAuth(accessToken)
            }
        }
        val body = response.decodeOrThrow<JsonElement>()
        check(body.isSuccess) { body.message }
        return true
    }

    override suspend fun updateName(id: Long, name: String): Boolean {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.patch(ApiConfig.url("/api/v1/destinations/$id")) {
                bearerAuth(accessToken)
                setBody(DestinationUpdateRequest(alias = name))
            }
        }
        val body = response.decodeOrThrow<DestinationResponse>()
        check(body.isSuccess) { body.message }
        checkNotNull(body.result) { "목적지 수정 응답이 비어 있어요." }
        return true
    }
}

@Serializable
private data class DestinationCreateRequest(
    val alias: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class DestinationSyncRequest(
    val destinations: List<DestinationSyncItemRequest>,
)

@Serializable
private data class DestinationSyncItemRequest(
    val clientDestinationId: Long,
    val alias: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class DestinationSyncResponse(
    val destinations: List<DestinationSyncItemResponse>,
)

@Serializable
private data class DestinationSyncItemResponse(
    val clientDestinationId: Long,
    val destinationId: Long,
    val alias: String,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class DestinationCreateResponse(
    val destinationId: Long,
)

@Serializable
private data class DestinationUpdateRequest(
    val alias: String,
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
