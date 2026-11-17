package com.joon.ringout.data.auth.local

import com.joon.ringout.platform.IosAnalyticsUserIdSetter
import eu.anifantakis.lib.ksafe.KSafe

internal fun createSecureTokenStorage(): AnalyticsTokenStorage =
    sharedTokenStorage

// Firebase 설정 직후, 인증 및 백그라운드 런타임이 시작되기 전에 한 번 연결한다.
internal fun connectAnalyticsUserId(setter: IosAnalyticsUserIdSetter) {
    analyticsUserIdSetter = setter
    sharedTokenStorage.synchronizeUserId()
}

private var analyticsUserIdSetter: IosAnalyticsUserIdSetter? = null

private val sharedTokenStorage: AnalyticsTokenStorage by lazy {
    val storage = KSafeTokenStorage(
        kSafe = KSafe(fileName = AUTH_VAULT_FILE_NAME),
    )
    AnalyticsTokenStorage(
        delegate = storage,
        tokenSnapshot = storage::readSnapshot,
        setUserId = { analyticsUserIdSetter?.setUserId(it) },
    )
}
