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

    override suspend fun read(): AuthTokens? =
        kSafe.get(key = AUTH_TOKENS_KEY, defaultValue = StoredAuthTokens()).toAuthTokens()

    /** 네이티브 알람 콜백에서 비동기 처리 전에 소유자를 고정하기 위한 로컬 저장소 조회. */
    internal fun readSnapshot(): AuthTokens? =
        kSafe.getDirect(key = AUTH_TOKENS_KEY, defaultValue = StoredAuthTokens()).toAuthTokens()

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

private fun StoredAuthTokens.toAuthTokens(): AuthTokens? =
    if (accessToken.isBlank() || refreshToken.isBlank()) null else AuthTokens(accessToken, refreshToken)

internal const val AUTH_VAULT_FILE_NAME = "auth_vault"
private const val AUTH_TOKENS_KEY = "auth_tokens"
