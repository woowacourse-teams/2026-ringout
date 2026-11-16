package com.joon.ringout.domain.auth

interface AuthDiagnostics {
    val isAvailable: Boolean
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
