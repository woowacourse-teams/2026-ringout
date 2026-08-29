package com.joon.ringout.data.auth.local

import com.joon.ringout.domain.auth.SecureTokenStorage
import eu.anifantakis.lib.ksafe.KSafe

internal fun createSecureTokenStorage(): SecureTokenStorage =
    sharedTokenStorage

private val sharedTokenStorage: SecureTokenStorage by lazy {
    KSafeTokenStorage(
        kSafe = KSafe(fileName = AUTH_VAULT_FILE_NAME),
    )
}
