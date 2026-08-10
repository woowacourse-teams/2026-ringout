package com.joon.ringout

import android.app.Application
import com.google.android.libraries.places.api.Places

class RingoutApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        check(BuildConfig.MAPS_API_KEY.isNotBlank()) {
            "MAPS_API_KEY must be set in local.properties or the environment"
        }
        Places.initializeWithNewPlacesApiEnabled(this, BuildConfig.MAPS_API_KEY)
    }
}
