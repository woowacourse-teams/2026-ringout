package com.joon.ringout.data.auth.local

import com.google.firebase.analytics.FirebaseAnalytics
import eu.anifantakis.lib.ksafe.KSafe

internal fun createSecureTokenStorage(context: android.content.Context): AnalyticsTokenStorage =
    sharedTokenStorage ?: synchronized(tokenStorageLock) {
        sharedTokenStorage ?: run {
            val applicationContext = context.applicationContext
            val storage = KSafeTokenStorage(
                kSafe = KSafe(
                    context = applicationContext,
                    fileName = AUTH_VAULT_FILE_NAME,
                ),
            )
            AnalyticsTokenStorage(
                delegate = storage,
                tokenSnapshot = storage::readSnapshot,
                setUserId = { FirebaseAnalytics.getInstance(applicationContext).setUserId(it) },
            ).also {
                // 앱 컨테이너와 백그라운드 런타임에 공개하기 전에 저장된 계정을 복원한다.
                it.synchronizeUserId()
                sharedTokenStorage = it
            }
        }
    }

private val tokenStorageLock = Any()

@Volatile
private var sharedTokenStorage: AnalyticsTokenStorage? = null
