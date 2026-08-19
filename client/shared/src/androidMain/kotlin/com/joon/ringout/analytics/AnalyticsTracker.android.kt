package com.joon.ringout.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

internal class FirebaseAnalyticsTracker(
    context: Context,
) : AnalyticsTracker {
    private val applicationContext = context.applicationContext
    private val firebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(applicationContext)
    }

    override fun log(event: AnalyticsEvent) {
        val parameters = Bundle().apply {
            event.parameters.forEach { (name, value) ->
                when (value) {
                    is AnalyticsParameterValue.Number -> putLong(name.wireName, value.value)
                    is AnalyticsParameterValue.Text -> putString(name.wireName, value.value)
                }
            }
        }
        firebaseAnalytics.logEvent(event.name.wireName, parameters)
    }
}
