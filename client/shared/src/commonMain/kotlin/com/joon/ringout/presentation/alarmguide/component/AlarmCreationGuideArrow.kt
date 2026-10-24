package com.joon.ringout.presentation.alarmguide.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.joon.ringout.RingoutTheme
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideStep
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ringout.shared.generated.resources.Res

internal data class AlarmCreationGuideArrowAsset(
    val fileName: String,
    val width: Dp,
    val height: Dp,
    val rotatedWidth: Dp,
    val rotatedHeight: Dp,
)

internal fun AlarmCreationGuideStep.arrowAsset(): AlarmCreationGuideArrowAsset = when (this) {
    AlarmCreationGuideStep.AddAlarm -> AlarmCreationGuideArrowAsset(
        "alarm_guide_add_arrow.svg", 128.612.dp, 37.2214.dp, 133.9.dp, 74.3.dp,
    )
    AlarmCreationGuideStep.Time, AlarmCreationGuideStep.Weekdays -> AlarmCreationGuideArrowAsset(
        "alarm_guide_time_arrow.svg", 51.9403.dp, 76.2849.dp, 72.55.dp, 88.41.dp,
    )
    AlarmCreationGuideStep.Destination -> AlarmCreationGuideArrowAsset(
        "alarm_guide_destination_arrow.svg", 96.2906.dp, 47.9204.dp, 106.26.dp, 74.74.dp,
    )
    AlarmCreationGuideStep.Interval, AlarmCreationGuideStep.Sound -> AlarmCreationGuideArrowAsset(
        "alarm_guide_interval_arrow.svg", 63.1184.dp, 72.9261.dp, 82.19.dp, 88.57.dp,
    )
    AlarmCreationGuideStep.CreateAlarm -> AlarmCreationGuideArrowAsset(
        "alarm_guide_create_arrow.svg", 50.4339.dp, 64.2493.dp, 67.47.dp, 76.48.dp,
    )
}

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun AlarmCreationGuideArrow(
    step: AlarmCreationGuideStep,
    modifier: Modifier = Modifier,
    isReversed: Boolean = false,
) {
    val asset = step.arrowAsset()
    var isLoaded by remember(asset.fileName) { mutableStateOf(false) }
    val bytes by produceState<ByteArray?>(null, asset.fileName) {
        value = Res.readBytes("drawable/${asset.fileName}")
    }
    val context = LocalPlatformContext.current
    val request = remember(context, bytes) {
        ImageRequest.Builder(context)
            .data(bytes)
            .decoderFactory(SvgDecoder.Factory())
            .build()
    }
    Box(
        modifier = modifier.size(asset.rotatedWidth, asset.rotatedHeight),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier
                .requiredSize(asset.width, asset.height)
                .testTag(if (isLoaded) "guide-arrow-loaded" else "guide-arrow")
                .graphicsLayer { rotationZ = 17.56f + if (isReversed) 180f else 0f },
            contentScale = ContentScale.Fit,
            onSuccess = { isLoaded = true },
        )
    }
}

@Preview
@Composable
private fun AlarmCreationGuideArrowPreview() {
    RingoutTheme { AlarmCreationGuideArrow(AlarmCreationGuideStep.AddAlarm) }
}
