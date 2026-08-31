package com.joon.ringout.data.auth.local

import com.joon.ringout.domain.auth.SecureTokenStorage
import eu.anifantakis.lib.ksafe.KSafe

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
