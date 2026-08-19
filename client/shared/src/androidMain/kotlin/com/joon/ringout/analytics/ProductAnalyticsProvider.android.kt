package com.joon.ringout.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberProductAnalyticsRecorder(): ProductAnalyticsRecorder {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        DefaultProductAnalyticsRecorder(
            tracker = FirebaseAnalyticsTracker(applicationContext),
            usageStore = AnalyticsUsageStore(applicationContext),
        )
    }
}
