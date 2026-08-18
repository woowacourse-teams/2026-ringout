package com.joon.ringout

import android.app.Application
import com.google.android.libraries.places.api.Places
import com.kakao.sdk.common.KakaoSdk

class RingoutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.MAPS_API_KEY.isNotBlank()) {
            "MAPS_API_KEY must be set in local.properties or the environment"
        }
        check(BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            "KAKAO_NATIVE_APP_KEY must be set in local.properties or the environment"
        }
        Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.MAPS_API_KEY)
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
