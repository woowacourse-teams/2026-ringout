package com.joon.ringout.data.auth

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.data.network.ApiException
import com.joon.ringout.domain.auth.AuthDiagnostics
import com.joon.ringout.domain.auth.AuthProbeResult
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.io.IOException
import kotlinx.serialization.Serializable

internal class DevelopmentAuthDiagnostics(
    private val client: HttpClient,
    private val tokens: SecureTokenStorage,
    private val session: AuthSession,
) : AuthDiagnostics {
    override val isAvailable = ApiConfig.BASE_URL.trimEnd('/') == "https://dev-api.ringout.my"

    private val returnAccount = MutableStateFlow<Long?>(null)
    override val hasPendingReturn: Boolean get() = returnAccount.value != null

    override suspend fun consumeReturnForCurrentAccount(): Boolean {
        val expected = returnAccount.getAndUpdate { null } ?: return false
        return tokens.read()?.userId == expected
    }

    override suspend fun revoke(refresh: Boolean) {
        check(isAvailable)
        val current = checkNotNull(tokens.read()) { "로그인이 필요해요." }
        val response = client.post(ApiConfig.url("/api/v1/auth/revoke")) {
            setBody(RevokeRequest(if (refresh) current.refreshToken else current.accessToken))
        }
        check(response.status.isSuccess()) { "폐기 실패 (HTTP ${response.status.value})" }
    }

    override suspend fun probe(
        requestCount: Int,
        failReissue: Boolean,
        revokeAccessFirst: Boolean,
        restoreAfterReauthentication: Boolean,
    ): AuthProbeResult {
        if (restoreAfterReauthentication) returnAccount.value = tokens.read()?.userId
        try {
            if (restoreAfterReauthentication) revoke(refresh = true)
            if (revokeAccessFirst) revoke(refresh = false)
            return runProbe(requestCount, failReissue)
        } finally {
            if (restoreAfterReauthentication && session.state.value != AuthSessionState.ReauthenticationRequired) {
                returnAccount.value = null
            }
        }
    }

    private suspend fun runProbe(requestCount: Int, failReissue: Boolean): AuthProbeResult = coroutineScope {
        check(isAvailable)
        require(requestCount > 0)
        val reissues = MutableStateFlow(0)
        val expired = MutableStateFlow(0)
        val retried = MutableStateFlow(0)
        val initialResponses = MutableStateFlow(0)
        val allInitialResponses = CompletableDeferred<Unit>()
        val executor = AuthenticatedRequestExecutor(client, tokens, session) {
            reissues.update { it + 1 }
            if (failReissue) throw IOException("Simulated connection failure")
        }
        val results = List(requestCount) {
            async {
                var attempts = 0
                try {
                    val response = executor.execute { accessToken ->
                        val initial = attempts++ == 0
                        val response = try {
                            client.get(ApiConfig.url("/api/v1/users/me")) { bearerAuth(accessToken) }
                        } finally {
                            // Let all initial requests use the old token before any can reissue it.
                            if (initial && initialResponses.updateAndGet { it + 1 } == requestCount) {
                                allInitialResponses.complete(Unit)
                            }
                        }
                        if (initial) {
                            if (response.status == HttpStatusCode.Unauthorized) expired.update { it + 1 }
                            allInitialResponses.await()
                        }
                        response
                    }
                    if (response.status.isSuccess() && attempts > 1) retried.update { it + 1 }
                    if (response.status.isSuccess()) null else "HTTP ${response.status.value}"
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // A request can fail before it reaches the HTTP call (e.g. session changed).
                    if (attempts == 0 && initialResponses.updateAndGet { it + 1 } == requestCount) {
                        allInitialResponses.complete(Unit)
                    }
                    when (error) {
                        is ApiException -> "HTTP ${error.statusCode} · ${error.code}"
                        is IOException -> "연결 오류"
                        else -> "요청 실패"
                    }
                }
            }
        }.awaitAll()
        AuthProbeResult(expired.value, reissues.value, retried.value, results.count { it == null }, results.firstOrNull { it != null })
    }
}

@Serializable
private data class RevokeRequest(val token: String)
