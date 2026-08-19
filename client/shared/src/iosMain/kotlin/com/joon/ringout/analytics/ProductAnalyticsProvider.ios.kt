package com.joon.ringout.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.platform.IosAnalyticsEventDto
import com.joon.ringout.platform.IosAnalyticsParameterDto
import com.joon.ringout.platform.IosAnalyticsTracker
import com.joon.ringout.platform.LocalIosNativeServices

@Composable
internal actual fun rememberProductAnalyticsRecorder(): ProductAnalyticsRecorder {
    val nativeTracker = LocalIosNativeServices.current.analyticsTracker()
    return remember(nativeTracker) {
        DefaultProductAnalyticsRecorder(
            tracker = IosProductAnalyticsTracker(nativeTracker),
            usageStore = IosAnalyticsUsageStore(),
        )
    }
}

private class IosProductAnalyticsTracker(
    private val delegate: IosAnalyticsTracker,
) : AnalyticsTracker {
    override fun log(event: AnalyticsEvent) {
        delegate.log(
            IosAnalyticsEventDto(
                name = event.name.wireName,
                parameters = event.parameters.map { (name, value) ->
                    when (value) {
                        is AnalyticsParameterValue.Number -> IosAnalyticsParameterDto(
                            name = name.wireName,
                            numberValue = value.value,
                        )

                        is AnalyticsParameterValue.Text -> IosAnalyticsParameterDto(
                            name = name.wireName,
                            textValue = value.value,
                        )
                    }
                },
            ),
        )
    }
}
