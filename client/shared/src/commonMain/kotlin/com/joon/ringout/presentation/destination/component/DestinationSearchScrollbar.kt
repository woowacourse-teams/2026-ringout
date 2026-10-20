package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollIndicatorState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme

@Composable
internal fun DestinationSearchScrollbar(
    state: ScrollIndicatorState,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.width(4.dp).fillMaxHeight()) {
        val contentSize = state.contentSize
        val viewportSize = state.viewportSize
        val scrollOffset = state.scrollOffset
        if (viewportSize <= 0 || contentSize <= viewportSize || scrollOffset < 0) return@Canvas

        val thumbHeight = (size.height * viewportSize.toFloat() / contentSize)
            .coerceAtLeast(24.dp.toPx())
            .coerceAtMost(size.height)
        val scrollFraction = (scrollOffset.toFloat() / (contentSize - viewportSize))
            .coerceIn(0f, 1f)
        val cornerRadius = CornerRadius(size.width / 2f)

        drawRoundRect(color = DestinationSearchColors.scrollbarTrack, cornerRadius = cornerRadius)
        drawRoundRect(
            color = DestinationSearchColors.scrollbarThumb,
            topLeft = Offset(0f, (size.height - thumbHeight) * scrollFraction),
            size = Size(size.width, thumbHeight),
            cornerRadius = cornerRadius,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DestinationSearchScrollbarPreview() {
    RingoutTheme {
        DestinationSearchScrollbar(
            state = object : ScrollIndicatorState {
                override val contentSize = 1000
                override val viewportSize = 280
                override val scrollOffset = 360
            },
            modifier = Modifier.height(280.dp),
        )
    }
}
