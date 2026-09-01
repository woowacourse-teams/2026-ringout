package com.joon.ringout.analytics

import android.content.Context

internal fun createProductAnalyticsRecorder(
    context: Context,
): ProductAnalyticsRecorder =
    DefaultProductAnalyticsRecorder(
        tracker = FirebaseAnalyticsTracker(context.applicationContext),
        usageStore = AnalyticsUsageStore(context.applicationContext),
    )
