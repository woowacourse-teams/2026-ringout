package com.joon.ringout.presentation.arrival.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.arrival.ArrivalGrowth
import org.jetbrains.compose.resources.ExperimentalResourceApi
import ringout.shared.generated.resources.Res

@OptIn(ExperimentalResourceApi::class)
@Composable
internal fun ArrivalGrowthProgress(
    growth: ArrivalGrowth,
    modifier: Modifier = Modifier,
) {
    val context = LocalPlatformContext.current
    val requests = remember(context) {
        listOf("arrival_progress_filled.svg", "arrival_progress_empty.svg").map { fileName ->
            ImageRequest.Builder(context)
                .data(Res.getUri("drawable/$fileName"))
                .decoderFactory(SvgDecoder.Factory())
                .build()
        }
    }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("arrival-growth-progress")
            .semantics(mergeDescendants = true) {
                contentDescription = "나무 성장 진행 상황"
                stateDescription = "5일 중 ${growth.filledSlots}일 도착"
            }
            .padding(horizontal = 20.dp, vertical = 7.dp),
    ) {
        // Keep every seed square and reserve its bounds while the SVG is loading.
        val seedSize = minOf(64.dp, maxWidth / 5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(5) { index ->
                val isFilled = index < growth.filledSlots
                val request = requests[if (isFilled) 0 else 1]
                var isLoaded by remember(request) { mutableStateOf(false) }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    modifier = Modifier.size(seedSize).testTag(
                        when {
                            !isLoaded -> "arrival-progress-loading"
                            isFilled -> "arrival-progress-filled-loaded"
                            else -> "arrival-progress-empty-loaded"
                        },
                    ),
                    contentScale = ContentScale.Fit,
                    onSuccess = { isLoaded = true },
                    onError = { isLoaded = false },
                )
            }
        }
    }
}

@Preview(name = "도착 4일", widthDp = 402, showBackground = true)
@Composable
private fun ArrivalGrowthProgressPreview() {
    RingoutTheme { ArrivalGrowthProgress(ArrivalGrowth(successfulDayCount = 4)) }
}

@Preview(name = "나무 완성", widthDp = 402, showBackground = true)
@Composable
private fun ArrivalGrowthProgressCompletePreview() {
    RingoutTheme { ArrivalGrowthProgress(ArrivalGrowth(successfulDayCount = 5)) }
}

@Preview(name = "작은 화면", widthDp = 320, showBackground = true)
@Composable
private fun ArrivalGrowthProgressCompactPreview() {
    RingoutTheme { ArrivalGrowthProgress(ArrivalGrowth(successfulDayCount = 4)) }
}
