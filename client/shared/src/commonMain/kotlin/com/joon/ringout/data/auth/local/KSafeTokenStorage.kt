package com.joon.ringout.data.auth.local

import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.serialization.Serializable

class KSafeTokenStorage(
    private val kSafe: KSafe,
) : SecureTokenStorage {
    override suspend fun save(tokens: AuthTokens) {
        kSafe.put(
            key = AUTH_TOKENS_KEY,
            value = StoredAuthTokens(
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
            ),
        )
    }

    override suspend fun read(): AuthTokens? {
        val storedTokens = kSafe.get(
            key = AUTH_TOKENS_KEY,
            defaultValue = StoredAuthTokens(),
        )
        if (storedTokens.accessToken.isBlank() || storedTokens.refreshToken.isBlank()) {
            return null
        }

        return AuthTokens(
            accessToken = storedTokens.accessToken,
            refreshToken = storedTokens.refreshToken,
        )
    }

    override suspend fun clear() {
        kSafe.delete(AUTH_TOKENS_KEY)
    }

    override suspend fun expire() {
        kSafe.put(
            key = AUTH_TOKENS_KEY,
            value = StoredAuthTokens(reauthenticationRequired = true),
        )
    }

    override suspend fun isReauthenticationRequired(): Boolean =
        kSafe.get(
            key = AUTH_TOKENS_KEY,
            defaultValue = StoredAuthTokens(),
        ).reauthenticationRequired
}

@Serializable
private data class StoredAuthTokens(
    val accessToken: String = "",
    val refreshToken: String = "",
    val reauthenticationRequired: Boolean = false,
)

internal const val AUTH_VAULT_FILE_NAME = "auth_vault"
private const val AUTH_TOKENS_KEY = "auth_tokens"
