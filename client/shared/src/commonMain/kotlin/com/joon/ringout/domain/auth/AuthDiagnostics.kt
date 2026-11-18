package com.joon.ringout.domain.auth

import kotlin.time.Instant

interface AuthDiagnostics {
    val isAvailable: Boolean
    suspend fun readTokenStatus(): AuthTokenStatuses?
    suspend fun revoke(refresh: Boolean)
    val hasPendingReturn: Boolean
    suspend fun consumeReturnForCurrentAccount(): Boolean
    suspend fun probe(
        requestCount: Int,
        failReissue: Boolean = false,
        revokeAccessFirst: Boolean = false,
        restoreAfterReauthentication: Boolean = false,
    ): AuthProbeResult
}

data class AuthProbeResult(
    val expired: Int,
    val reissues: Int,
    val retriedSuccessfully: Int,
    val succeeded: Int,
    val failure: String?,
)

data class AuthTokenStatuses(
    val access: AuthTokenStatus,
    val refresh: AuthTokenStatus,
)

data class AuthTokenStatus(
    val expiresAt: Instant?,
    val expired: Boolean?,
    val revoked: Boolean,
)
