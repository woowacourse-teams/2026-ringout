package com.joon.ringout.data.auth

import com.joon.ringout.data.auth.remote.KtorAuthApi
import com.joon.ringout.data.auth.remote.model.ReissueRequest
import com.joon.ringout.data.network.ApiErrorResponse
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.diagnostics.AuthDiagnosticLogger
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.auth.getAuthSession
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonElement

internal class AuthenticatedRequestExecutor(
    private val httpClient: HttpClient,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession = getAuthSession(),
) {
    suspend fun execute(
        request: suspend (accessToken: String) -> HttpResponse,
    ): HttpResponse {
        val requestTokens = checkNotNull(tokenStorage.read()) {
            "로그인이 필요한 기능이에요."
        }
        val response = request(requestTokens.accessToken)
        if (response.status != HttpStatusCode.Unauthorized) {
            return response
        }

        val failure = response.toApiFailure()
        return when (failure.code) {
            ACCESS_TOKEN_EXPIRED_CODE -> {
                val refreshedTokens = refreshTokens(
                    rejectedTokens = requestTokens,
                    originalFailure = failure,
                )
                retryOnce(
                    tokens = refreshedTokens,
                    originalFailure = failure,
                    request = request,
                )
            }

            COMMON_UNAUTHORIZED_CODE -> {
                expireSessionIfUnchanged(requestTokens)
                throw failure.toException()
            }

            else -> throw failure.toException()
        }
    }

    private suspend fun refreshTokens(
        rejectedTokens: AuthTokens,
        originalFailure: ApiFailure,
    ): AuthTokens {
        val decision = tokenStateMutex.withLock {
            val currentTokens = tokenStorage.read()
            when {
                currentTokens == null -> RefreshDecision.Fail(originalFailure.toException())

                currentTokens != rejectedTokens -> {
                    val refreshTransition = lastRefreshTransition
                    if (
                        refreshTransition?.tokenStorage === tokenStorage &&
                        refreshTransition.rejectedTokens == rejectedTokens &&
                        refreshTransition.refreshedTokens == currentTokens
                    ) {
                        RefreshDecision.Use(currentTokens)
                    } else {
                        RefreshDecision.Fail(originalFailure.toException())
                    }
                }

                else -> {
                    val activeAttempt = activeRefreshAttempts.firstOrNull { attempt ->
                        attempt.tokenStorage === tokenStorage &&
                            attempt.rejectedTokens == rejectedTokens
                    }
                    if (activeAttempt != null) {
                        RefreshDecision.Await(activeAttempt)
                    } else {
                        RefreshAttempt(
                            tokenStorage = tokenStorage,
                            rejectedTokens = rejectedTokens,
                        ).also(activeRefreshAttempts::add).let(RefreshDecision::Start)
                    }
                }
            }
        }

        return when (decision) {
            is RefreshDecision.Use -> decision.tokens
            is RefreshDecision.Fail -> throw decision.error
            is RefreshDecision.Await -> decision.attempt.result.await().getOrThrow()
            is RefreshDecision.Start -> {
                startRefreshAttempt(
                    attempt = decision.attempt,
                    originalFailure = originalFailure,
                )
                decision.attempt.result.await().getOrThrow()
            }
        }
    }

    private fun startRefreshAttempt(
        attempt: RefreshAttempt,
        originalFailure: ApiFailure,
    ) {
        tokenRefreshScope.launch {
            try {
                val response = KtorAuthApi(httpClient).reissue(
                    ReissueRequest(refreshToken = attempt.rejectedTokens.refreshToken),
                )
                if (!response.isSuccess) {
                    throw ApiException(
                        statusCode = HttpStatusCode.OK.value,
                        code = response.code,
                        apiMessage = response.message,
                    )
                }
                val result = checkNotNull(response.result) {
                    "토큰 재발급 응답이 비어 있어요."
                }
                withContext(NonCancellable) {
                    completeRefreshSuccessfully(
                        attempt = attempt,
                        refreshedTokens = AuthTokens(
                            accessToken = result.accessToken,
                            refreshToken = result.refreshToken,
                        ),
                        originalFailure = originalFailure,
                    )
                }
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    completeRefreshExceptionally(
                        attempt = attempt,
                        error = error,
                        shouldExpireSession = error is ApiException &&
                            (
                                error.statusCode == HttpStatusCode.Unauthorized.value ||
                                    error.code == COMMON_UNAUTHORIZED_CODE
                            ),
                    )
                }
            }
        }
    }

    private suspend fun completeRefreshSuccessfully(
        attempt: RefreshAttempt,
        refreshedTokens: AuthTokens,
        originalFailure: ApiFailure,
    ) {
        tokenStateMutex.withLock {
            val outcome: Result<AuthTokens> = if (tokenStorage.read() == attempt.rejectedTokens) {
                tokenStorage.save(refreshedTokens)
                lastRefreshTransition = RefreshTransition(
                    tokenStorage = tokenStorage,
                    rejectedTokens = attempt.rejectedTokens,
                    refreshedTokens = refreshedTokens,
                )
                Result.success(refreshedTokens)
            } else {
                Result.failure(originalFailure.toException())
            }
            activeRefreshAttempts.remove(attempt)
            attempt.result.complete(outcome)
        }
    }

    private suspend fun completeRefreshExceptionally(
        attempt: RefreshAttempt,
        error: Throwable,
        shouldExpireSession: Boolean,
    ) {
        tokenStateMutex.withLock {
            val completionError = try {
                if (shouldExpireSession) {
                    expireSessionLockedIfUnchanged(attempt.rejectedTokens)
                }
                error
            } catch (cleanupError: Throwable) {
                AuthDiagnosticLogger.error(
                    message = "token_refresh_cleanup_failed",
                    cause = cleanupError,
                )
                cleanupError
            }
            activeRefreshAttempts.remove(attempt)
            attempt.result.complete(Result.failure(completionError))
        }
    }

    private suspend fun retryOnce(
        tokens: AuthTokens,
        originalFailure: ApiFailure,
        request: suspend (accessToken: String) -> HttpResponse,
    ): HttpResponse {
        val tokensAreCurrent = tokenStateMutex.withLock {
            tokenStorage.read() == tokens
        }
        if (!tokensAreCurrent) {
            throw originalFailure.toException()
        }

        val response = request(tokens.accessToken)
        if (response.status != HttpStatusCode.Unauthorized) {
            return response
        }

        val failure = response.toApiFailure()
        if (
            failure.code == ACCESS_TOKEN_EXPIRED_CODE ||
            failure.code == COMMON_UNAUTHORIZED_CODE
        ) {
            expireSessionIfUnchanged(tokens)
        }
        throw failure.toException()
    }

    private suspend fun expireSessionIfUnchanged(expectedTokens: AuthTokens) {
        withContext(NonCancellable) {
            tokenStateMutex.withLock {
                expireSessionLockedIfUnchanged(expectedTokens)
            }
        }
    }

    private suspend fun expireSessionLockedIfUnchanged(expectedTokens: AuthTokens) {
        if (tokenStorage.read() == expectedTokens) {
            expireSessionLocked()
        }
    }

    private suspend fun expireSessionLocked() {
        tokenStorage.expire()
        if (lastRefreshTransition?.tokenStorage === tokenStorage) {
            lastRefreshTransition = null
        }
        authSession.requireReauthentication()
    }
}

private sealed interface RefreshDecision {
    data class Use(val tokens: AuthTokens) : RefreshDecision

    data class Await(val attempt: RefreshAttempt) : RefreshDecision

    data class Start(val attempt: RefreshAttempt) : RefreshDecision

    data class Fail(val error: ApiException) : RefreshDecision
}

private data class RefreshAttempt(
    val tokenStorage: SecureTokenStorage,
    val rejectedTokens: AuthTokens,
    val result: CompletableDeferred<Result<AuthTokens>> = CompletableDeferred(),
)

private data class RefreshTransition(
    val tokenStorage: SecureTokenStorage,
    val rejectedTokens: AuthTokens,
    val refreshedTokens: AuthTokens,
)

private data class ApiFailure(
    val statusCode: Int,
    val code: String?,
    val message: String,
    val result: JsonElement?,
) {
    fun toException(): ApiException = ApiException(
        statusCode = statusCode,
        code = code,
        apiMessage = message,
        result = result,
    )
}

private suspend fun HttpResponse.toApiFailure(): ApiFailure {
    val responseBody = bodyAsText()
    val errorResponse = try {
        ApiJson.decodeFromString<ApiErrorResponse>(responseBody)
    } catch (_: SerializationException) {
        null
    }
    return ApiFailure(
        statusCode = status.value,
        code = errorResponse?.code,
        message = errorResponse?.message ?: status.description,
        result = errorResponse?.result,
    )
}

private val tokenStateMutex = Mutex()
private val tokenRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val activeRefreshAttempts = mutableListOf<RefreshAttempt>()
private var lastRefreshTransition: RefreshTransition? = null

internal suspend fun SecureTokenStorage.restoreAuthSession(authSession: AuthSession) {
    withContext(NonCancellable) {
        tokenStateMutex.withLock {
            when {
                read() != null -> authSession.markAuthenticated()
                isReauthenticationRequired() ||
                    authSession.state.value == AuthSessionState.ReauthenticationRequired -> {
                    authSession.requireReauthentication()
                }

                else -> authSession.clear()
            }
        }
    }
}

internal suspend fun SecureTokenStorage.replaceAuthTokens(
    tokens: AuthTokens,
    authSession: AuthSession,
) {
    withContext(NonCancellable) {
        tokenStateMutex.withLock {
            save(tokens)
            if (lastRefreshTransition?.tokenStorage === this@replaceAuthTokens) {
                lastRefreshTransition = null
            }
            authSession.markAuthenticated()
        }
    }
}

internal suspend fun SecureTokenStorage.removeAuthTokens(authSession: AuthSession) {
    withContext(NonCancellable) {
        tokenStateMutex.withLock {
            clear()
            if (lastRefreshTransition?.tokenStorage === this@removeAuthTokens) {
                lastRefreshTransition = null
            }
            authSession.clear()
        }
    }
}

private const val ACCESS_TOKEN_EXPIRED_CODE = "AUTH401"
private const val COMMON_UNAUTHORIZED_CODE = "COMMON401"
