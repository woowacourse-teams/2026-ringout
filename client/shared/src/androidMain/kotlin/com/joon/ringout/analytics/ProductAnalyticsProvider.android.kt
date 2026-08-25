package com.joon.ringout.analytics

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal fun createProductAnalyticsRecorder(
    context: Context,
): ProductAnalyticsRecorder =
    DefaultProductAnalyticsRecorder(
        tracker = FirebaseAnalyticsTracker(context.applicationContext),
        usageStore = AnalyticsUsageStore(context.applicationContext),
    )

@Composable
internal actual fun rememberProductAnalyticsRecorder(): ProductAnalyticsRecorder {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        createProductAnalyticsRecorder(applicationContext)
    }
}
