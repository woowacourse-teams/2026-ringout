package com.joon.ringout.data.auth.local

import eu.anifantakis.lib.ksafe.KSafe

internal fun createSecureTokenStorage(): KSafeTokenStorage =
    sharedTokenStorage

private val sharedTokenStorage: KSafeTokenStorage by lazy {
    KSafeTokenStorage(
        kSafe = KSafe(fileName = AUTH_VAULT_FILE_NAME),
    )
}
