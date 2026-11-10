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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
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
        val body = response.decodeOrThrow<List<DestinationResponse>>()
        requireSuccess(body)
        return checkNotNull(body.result) { "목적지 조회 응답이 비어 있어요." }
            .map(DestinationResponse::toRemote)
    }

    override suspend fun create(
        ownerAccountId: String,
        destination: RemoteSavedDestination,
    ): RemoteSavedDestination {
        require(destination.id.isNotBlank()) { "목적지 ID가 비어 있어요." }
        require(destination.id.toServerDestinationIdOrNull() == null) {
            "서버 ID가 이미 할당된 목적지는 생성할 수 없어요."
        }
        val response = authenticatedRequests.execute { accessToken ->
            check(alarmOccurrenceTokenOwner(accessToken) == ownerAccountId) {
                "현재 로그인 계정이 목적지 계정과 일치하지 않아요."
            }
            val request = DestinationWriteRequest(
                alias = destination.alias,
                latitude = destination.latitude,
                longitude = destination.longitude,
            )
            httpClient.post(ApiConfig.url("/api/v1/destinations")) {
                bearerAuth(accessToken)
                setBody(request)
            }
        }

        val body = response.decodeOrThrow<DestinationCreateResponse>(HttpStatusCode.Created)
        requireSuccess(body, DestinationCreatedSuccessCode)
        val createdId = checkNotNull(body.result) { "목적지 생성 응답이 비어 있어요." }
            .destinationId
            .toString()
        return destination.copy(id = createdId)
    }

    override suspend fun delete(ownerAccountId: String, destinationId: String) {
        val serverId = destinationId.toServerDestinationIdOrNull()
            ?: throw IllegalArgumentException("서버 목적지 ID는 양의 정수여야 해요.")
        val response = authenticatedRequests.execute { accessToken ->
            check(alarmOccurrenceTokenOwner(accessToken) == ownerAccountId) {
                "현재 로그인 계정이 목적지 계정과 일치하지 않아요."
            }
            httpClient.delete(destinationUrl(serverId)) {
                bearerAuth(accessToken)
            }
        }
        val body = response.decodeOrThrow<JsonElement>()
        requireSuccess(body)
        check(body.result == null) { "목적지 삭제 응답은 result를 포함하지 않아야 해요." }
    }

    private fun destinationUrl(destinationId: Long): String =
        ApiConfig.url("/api/v1/destinations/$destinationId")
}

@Serializable
private data class DestinationWriteRequest(
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

private fun DestinationResponse.toRemote(version: Long = 1L): RemoteSavedDestination = RemoteSavedDestination(
    id = destinationId.toString(),
    alias = alias,
    latitude = latitude,
    longitude = longitude,
    version = version,
)

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
private const val DestinationCreatedSuccessCode = "DESTINATION201"

internal fun String.toServerDestinationIdOrNull(): Long? =
    toLongOrNull()?.takeIf { it > 0L }
