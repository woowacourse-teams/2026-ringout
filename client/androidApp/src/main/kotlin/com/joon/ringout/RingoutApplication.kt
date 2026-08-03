package com.joon.ringout

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class RingoutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()) {
            "KAKAO_NATIVE_APP_KEY must be set in local.properties or the environment"
        }
        KakaoMapSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }
}
