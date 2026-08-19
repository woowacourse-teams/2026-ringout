package com.joon.ringout.data.auth.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.domain.auth.SecureTokenStorage
import eu.anifantakis.lib.ksafe.KSafe

@Composable
internal actual fun rememberSecureTokenStorage(): SecureTokenStorage = remember {
    createSecureTokenStorage()
}

internal fun createSecureTokenStorage(): SecureTokenStorage =
    sharedTokenStorage

private val sharedTokenStorage: SecureTokenStorage by lazy {
    KSafeTokenStorage(
        kSafe = KSafe(fileName = AUTH_VAULT_FILE_NAME),
    )
}
