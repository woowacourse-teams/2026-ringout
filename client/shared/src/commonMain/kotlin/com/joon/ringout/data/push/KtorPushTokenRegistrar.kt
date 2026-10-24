package com.joon.ringout.data.push

import com.joon.ringout.data.auth.AuthenticatedRequestExecutor
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.push.PushPlatform
import com.joon.ringout.domain.push.PushTokenRegistrationCandidate
import com.joon.ringout.domain.push.PushTokenRegistrar
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement

class KtorPushTokenRegistrar(
    private val httpClient: HttpClient,
    tokenStorage: SecureTokenStorage,
    authSession: AuthSession,
) : PushTokenRegistrar {
    private val authenticatedRequests = AuthenticatedRequestExecutor(httpClient, tokenStorage, authSession)

    override suspend fun register(candidate: PushTokenRegistrationCandidate) {
        val response = authenticatedRequests.execute { accessToken ->
            httpClient.post(ApiConfig.url("/api/v1/device-tokens")) {
                bearerAuth(accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    DeviceTokenRegisterRequest(
                        platform = candidate.platform.toApiValue(),
                        fcmToken = candidate.fcmToken,
                    ),
                )
            }
        }
        val body = response.decodeRegistrationResponse()
        check(body.isSuccess) { body.message }
    }
}

private fun PushPlatform.toApiValue(): String = when (this) {
    PushPlatform.Android -> "ANDROID"
    PushPlatform.Ios -> "IOS"
}

@Serializable
private data class DeviceTokenRegisterRequest(
    val platform: String,
    val fcmToken: String,
)

private suspend fun HttpResponse.decodeRegistrationResponse(): ApiResponse<JsonElement> {
    val responseBody = bodyAsText()
    if (!status.isSuccess()) {
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
    return ApiJson.decodeFromString(responseBody)
}
