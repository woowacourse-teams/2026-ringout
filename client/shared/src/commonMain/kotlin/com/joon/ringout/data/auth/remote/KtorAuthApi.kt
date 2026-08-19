package com.joon.ringout.data.auth.remote

import com.joon.ringout.data.auth.remote.model.LoginRequest
import com.joon.ringout.data.auth.remote.model.LoginResponse
import com.joon.ringout.data.auth.remote.model.ReissueRequest
import com.joon.ringout.data.auth.remote.model.ReissueResponse
import com.joon.ringout.data.auth.remote.model.SignupRequest
import com.joon.ringout.data.auth.remote.model.SignupResponse
import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.ApiResponse
import com.joon.ringout.diagnostics.AuthDiagnosticLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.decodeFromString

class KtorAuthApi(
    private val httpClient: HttpClient,
) : AuthApi {
    override suspend fun login(
        provider: AuthProvider,
        request: LoginRequest,
    ): ApiResponse<LoginResponse> {
        AuthDiagnosticLogger.debug("http_login_request provider=${provider.pathValue}")
        return try {
            val response = httpClient.post(
                ApiConfig.url("/api/v1/auth/${provider.pathValue}/login"),
            ) {
                setBody(request)
            }
            AuthDiagnosticLogger.debug(
                "http_login_response provider=${provider.pathValue} status=${response.status.value}",
            )
            response.decodeOrThrow()
        } catch (error: Throwable) {
            AuthDiagnosticLogger.error(
                "http_login_failed provider=${provider.pathValue}",
                error,
            )
            throw error
        }
    }

    override suspend fun signup(
        signupToken: String,
        request: SignupRequest,
    ): ApiResponse<SignupResponse> {
        require(signupToken.isNotBlank()) { "Signup token must not be blank." }

        val response = httpClient.post(
            ApiConfig.url("/api/v1/auth/signup"),
        ) {
            bearerAuth(signupToken)
            setBody(request)
        }

        return response.decodeOrThrow()
    }

    override suspend fun reissue(
        request: ReissueRequest,
    ): ApiResponse<ReissueResponse> {
        val response = httpClient.post(
            ApiConfig.url("/api/v1/auth/reissue"),
        ) {
            setBody(request)
        }

        return response.decodeOrThrow()
    }
}

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
