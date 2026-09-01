package com.joon.ringout

import android.app.Application
import com.google.android.libraries.places.api.Places
import com.joon.ringout.di.AndroidAppContainer
import com.joon.ringout.di.AppContainer

class RingoutApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.MAPS_API_KEY.isNotBlank()) {
            "MAPS_API_KEY must be set in local.properties or the environment"
        }
        Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.MAPS_API_KEY)
        // TODO(RINGOUT_ACCOUNT): 카카오 로그인을 재도입할 때 KakaoSdk 초기화를 복구한다.
        appContainer = AndroidAppContainer(this)
    }
}
