package com.joon.ringout.data.auth.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.joon.ringout.domain.auth.SecureTokenStorage
import eu.anifantakis.lib.ksafe.KSafe

@Composable
internal actual fun rememberSecureTokenStorage(): SecureTokenStorage {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        createSecureTokenStorage(applicationContext)
    }
}

internal fun createSecureTokenStorage(context: android.content.Context): SecureTokenStorage =
    sharedTokenStorage ?: synchronized(tokenStorageLock) {
        sharedTokenStorage ?: KSafeTokenStorage(
            kSafe = KSafe(
                context = context.applicationContext,
                fileName = AUTH_VAULT_FILE_NAME,
            ),
        ).also { storage ->
            sharedTokenStorage = storage
        }
    }

private val tokenStorageLock = Any()

@Volatile
private var sharedTokenStorage: SecureTokenStorage? = null
