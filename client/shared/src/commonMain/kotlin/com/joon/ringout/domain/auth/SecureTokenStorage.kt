package com.joon.ringout.domain.auth

interface SecureTokenStorage {
    suspend fun save(tokens: AuthTokens)

    suspend fun read(): AuthTokens?

    suspend fun clear()
}
