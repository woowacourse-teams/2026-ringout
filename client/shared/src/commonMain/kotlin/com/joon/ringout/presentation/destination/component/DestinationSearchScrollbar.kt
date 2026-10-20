package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import kotlinx.coroutines.launch

internal val DestinationSearchScrollbarTouchWidth = 48.dp

@Composable
internal fun DestinationSearchScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val indicator = state.scrollIndicatorState ?: return
    val scope = rememberCoroutineScope()
    val canScroll = state.canScrollForward || state.canScrollBackward
    val scrollToFraction: (Float) -> Unit = { fraction ->
        scope.launch {
            when {
                state.layoutInfo.totalItemsCount == 0 -> Unit
                fraction <= 0f -> state.scrollToItem(0)
                fraction >= 1f -> state.scrollToItem(state.layoutInfo.totalItemsCount - 1)
                else -> {
                    val range = (indicator.contentSize - indicator.viewportSize).coerceAtLeast(0)
                    state.scrollBy(range * fraction - indicator.scrollOffset)
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .width(DestinationSearchScrollbarTouchWidth)
            .fillMaxHeight()
            .then(
                if (canScroll) {
                    Modifier
                        .semantics {
                            contentDescription = "검색 결과 스크롤"
                            val range = (indicator.contentSize - indicator.viewportSize).coerceAtLeast(1)
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                (indicator.scrollOffset.toFloat() / range).coerceIn(0f, 1f),
                                0f..1f,
                            )
                            setProgress { fraction ->
                                scrollToFraction(fraction.coerceIn(0f, 1f))
                                true
                            }
                        }
                        .pointerInput(state) {
                            var thumbTop = 0f
                            var thumbTravel = 0f
                            detectVerticalDragGestures(
                                onDragStart = {
                                    val geometry = indicator.thumbGeometry(size.height.toFloat(), 24.dp.toPx())
                                    thumbTop = geometry?.top ?: 0f
                                    thumbTravel = geometry?.travel ?: 0f
                                },
                                onVerticalDrag = { change, delta ->
                                    change.consume()
                                    if (thumbTravel > 0f) {
                                        thumbTop = (thumbTop + delta).coerceIn(0f, thumbTravel)
                                        scrollToFraction(thumbTop / thumbTravel)
                                    }
                                },
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        val geometry = indicator.thumbGeometry(size.height, 24.dp.toPx()) ?: return@Canvas
        val barWidth = 4.dp.toPx()
        val barLeft = size.width - barWidth
        val cornerRadius = CornerRadius(barWidth / 2f)

        drawRoundRect(
            color = DestinationSearchColors.scrollbarTrack,
            topLeft = Offset(barLeft, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = DestinationSearchColors.scrollbarThumb,
            topLeft = Offset(barLeft, geometry.top),
            size = Size(barWidth, geometry.height),
            cornerRadius = cornerRadius,
        )
    }
}

private data class ScrollbarThumbGeometry(val top: Float, val height: Float, val travel: Float)

private fun ScrollIndicatorState.thumbGeometry(trackHeight: Float, minThumbHeight: Float): ScrollbarThumbGeometry? {
    val content = contentSize
    val viewport = viewportSize
    val offset = scrollOffset
    if (viewport <= 0 || content <= viewport || offset < 0 || trackHeight <= 0f ||
        content == Int.MAX_VALUE || viewport == Int.MAX_VALUE || offset == Int.MAX_VALUE
    ) return null

    val height = (trackHeight * viewport.toFloat() / content)
        .coerceAtLeast(minThumbHeight)
        .coerceAtMost(trackHeight)
    val travel = trackHeight - height
    val fraction = (offset.toFloat() / (content - viewport)).coerceIn(0f, 1f)
    return ScrollbarThumbGeometry(top = travel * fraction, height = height, travel = travel)
}

@Preview(showBackground = true)
@Composable
private fun DestinationSearchScrollbarPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        val state = rememberLazyListState()
        Box(Modifier.width(240.dp).height(280.dp).background(MaterialTheme.colorScheme.surface)) {
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize().padding(end = DestinationSearchScrollbarTouchWidth),
            ) {
                items(15) { index ->
                    Text("강남역 ${index + 1}번 출구", modifier = Modifier.height(56.dp))
                }
            }
            DestinationSearchScrollbar(state = state, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}
