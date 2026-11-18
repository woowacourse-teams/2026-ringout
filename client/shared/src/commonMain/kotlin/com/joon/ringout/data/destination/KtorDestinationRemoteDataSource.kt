package com.joon.ringout.data.destination

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.alarmoccurrence.alarmOccurrenceTokenOwner
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
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement

class KtorDestinationRemoteDataSource(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
) : DestinationRemoteDataSource {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage)

    override suspend fun fetchAll(ownerAccountId: String): List<RemoteSavedDestination> {
        val response = authenticatedRequests.execute { accessToken ->
            check(alarmOccurrenceTokenOwner(accessToken) == ownerAccountId) {
                "현재 로그인 계정이 목적지 계정과 일치하지 않아요."
            }
            httpClient.get(ApiConfig.url("/api/v1/destinations")) {
                bearerAuth(accessToken)
            }
        }
        val body = response.decodeOrThrow<DestinationListResponse>()
        requireSuccess(body)
        return checkNotNull(body.result) { "목적지 조회 응답이 비어 있어요." }
            .destinations
            .map(DestinationResponse::toRemote)
    }

    override suspend fun upsert(
        ownerAccountId: String,
        destination: RemoteSavedDestination,
    ): RemoteSavedDestination {
        requireValidDestinationId(destination.id)
        val response = authenticatedRequests.execute { accessToken ->
            check(alarmOccurrenceTokenOwner(accessToken) == ownerAccountId) {
                "현재 로그인 계정이 목적지 계정과 일치하지 않아요."
            }
            val request = DestinationWriteRequest(
                alias = destination.alias,
                latitude = destination.latitude,
                longitude = destination.longitude,
                destinationVersion = destination.version,
            )
            httpClient.put(destinationUrl(destination.id)) {
                bearerAuth(accessToken)
                setBody(request)
            }
        }

        val body = response.decodeOrThrow<DestinationResponse>()
        requireSuccess(body)
        return checkNotNull(body.result) { "목적지 upsert 응답이 비어 있어요." }.toRemote()
    }

    override suspend fun delete(ownerAccountId: String, destinationId: String) {
        requireValidDestinationId(destinationId)
        val response = authenticatedRequests.execute { accessToken ->
            check(alarmOccurrenceTokenOwner(accessToken) == ownerAccountId) {
                "현재 로그인 계정이 목적지 계정과 일치하지 않아요."
            }
            httpClient.delete(destinationUrl(destinationId)) {
                bearerAuth(accessToken)
            }
        }
        val body = response.decodeOrThrow<JsonElement>()
        requireSuccess(body)
        check(body.result == null) { "목적지 삭제 응답은 result를 포함하지 않아야 해요." }
    }

    private fun destinationUrl(destinationId: String): String =
        URLBuilder(ApiConfig.url("/api/v1/destinations"))
            .apply { appendPathSegments(destinationId, encodeSlash = true) }
            .buildString()
}

@Serializable
private data class DestinationWriteRequest(
    val alias: String,
    val latitude: Double,
    val longitude: Double,
    val destinationVersion: Long,
)

@Serializable
private data class DestinationListResponse(
    val destinations: List<DestinationResponse>,
)

@Serializable
private data class DestinationResponse(
    val destinationId: String,
    val alias: String,
    val latitude: Double,
    val longitude: Double,
    val destinationVersion: Long,
)

private fun DestinationResponse.toRemote(): RemoteSavedDestination = RemoteSavedDestination(
    id = destinationId,
    alias = alias,
    latitude = latitude,
    longitude = longitude,
    version = destinationVersion,
)

private fun requireValidDestinationId(destinationId: String) {
    require(destinationId.length in 1..36 && destinationId.none(Char::isWhitespace)) {
        "목적지 ID는 공백 없이 1~36자여야 해요."
    }
}

private fun requireSuccess(response: ApiResponse<*>, expectedCode: String = DestinationSuccessCode) {
    check(response.isSuccess && response.code == expectedCode) { response.message }
}

private suspend inline fun <reified T> HttpResponse.decodeOrThrow(
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
): ApiResponse<T> {
    val responseBody = bodyAsText()
    if (status == expectedStatus) {
        return ApiJson.decodeFromString(responseBody)
    }

    val errorResponse = runCatching {
        ApiJson.decodeFromString<ApiErrorResponse>(responseBody)
    }.getOrNull()
    if (status.isSuccess()) {
        throw ApiException(
            statusCode = status.value,
            code = errorResponse?.code,
            apiMessage = errorResponse?.message ?: "목적지 API가 예상하지 않은 성공 상태를 반환했어요.",
            result = errorResponse?.result,
        )
    }
    throw ApiException(
        statusCode = status.value,
        code = errorResponse?.code,
        apiMessage = errorResponse?.message ?: status.description,
        result = errorResponse?.result,
    )
}

private const val DestinationSuccessCode = "DESTINATION200"
