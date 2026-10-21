package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
internal fun <T> TimePickerWheel(
    values: List<T>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    valueLabel: (T) -> String,
    accessibilityLabel: String,
    selectedColor: Color,
    unselectedColor: Color,
    selectedFontSize: TextUnit,
    unselectedFontSize: TextUnit,
    fontWeight: FontWeight,
    isLooping: Boolean,
    hapticFeedbackType: HapticFeedbackType,
    itemHeight: Dp,
    modifier: Modifier = Modifier,
) {
    require(values.isNotEmpty())

    val loopMiddle = remember(values) {
        val middle = Int.MAX_VALUE / 2
        middle - middle % values.size
    }
    val selectedValueIndex = values.indexOf(selectedValue).coerceAtLeast(0)
    val initialIndex = remember(values, isLooping) {
        if (isLooping) loopMiddle + selectedValueIndex else selectedValueIndex
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentSelectedValue by rememberUpdatedState(selectedValue)
    val currentHapticFeedback by rememberUpdatedState(LocalHapticFeedback.current)
    val currentHapticFeedbackType by rememberUpdatedState(hapticFeedbackType)

    LaunchedEffect(listState, values, isLooping) {
        var lastCenteredIndex: Int? = null
        snapshotFlow { listState.centeredItemIndex() }.collect { centeredIndex ->
            if (centeredIndex != null && centeredIndex != lastCenteredIndex) {
                lastCenteredIndex = centeredIndex
                val value = values.valueAt(centeredIndex, isLooping)
                if (value != currentSelectedValue) {
                    currentHapticFeedback.performHapticFeedback(currentHapticFeedbackType)
                    currentOnValueChange(value)
                }
            }
        }
    }

    LaunchedEffect(selectedValue, values, isLooping, listState) {
        val centeredIndex = snapshotFlow {
            if (listState.isScrollInProgress) null else listState.centeredItemIndex()
        }.filterNotNull().first()
        val centeredValue = values.valueAt(centeredIndex, isLooping)

        if (centeredValue != selectedValue) {
            listState.scrollToItem(
                if (isLooping) {
                    listState.nearestLoopIndex(
                        valuesSize = values.size,
                        targetValueIndex = selectedValueIndex,
                    )
                } else {
                    selectedValueIndex
                },
            )
        }
    }

    LazyColumn(
        state = listState,
        flingBehavior = flingBehavior,
        modifier = modifier
            .then(
                if (isLooping) {
                    Modifier
                } else {
                    Modifier.nestedScroll(BlockParentVerticalScrollConnection)
                },
            )
            .height(itemHeight * 3)
            .semantics {
                contentDescription = accessibilityLabel
                stateDescription = valueLabel(selectedValue)
            },
        contentPadding = PaddingValues(vertical = itemHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count = if (isLooping) Int.MAX_VALUE else values.size) { index ->
            val centerFraction by remember(index) {
                derivedStateOf { listState.centerFractionFor(index) }
            }
            val value = values.valueAt(index, isLooping)
            val fontSize = (
                unselectedFontSize.value +
                    (selectedFontSize.value - unselectedFontSize.value) * centerFraction
            ).sp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable {
                        coroutineScope.launch {
                            listState.animateScrollToItem(index)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = valueLabel(value),
                    color = lerp(unselectedColor, selectedColor, centerFraction),
                    maxLines = 1,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        fontWeight = fontWeight,
                    ),
                )
            }
        }
    }
}

private fun <T> List<T>.valueAt(index: Int, isLooping: Boolean): T =
    get(if (isLooping) index.floorMod(size) else index)

private fun LazyListState.centeredItemIndex(): Int? {
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return layoutInfo.visibleItemsInfo.minByOrNull { item ->
        abs(item.offset + item.size / 2f - viewportCenter)
    }?.index
}

private fun LazyListState.centerFractionFor(index: Int): Float {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val distance = abs(item.offset + item.size / 2f - viewportCenter)
    return (1f - distance / item.size).coerceIn(0f, 1f)
}

private fun LazyListState.nearestLoopIndex(
    valuesSize: Int,
    targetValueIndex: Int,
): Int {
    val currentIndex = centeredItemIndex() ?: firstVisibleItemIndex
    val currentValueIndex = currentIndex.floorMod(valuesSize)
    var delta = targetValueIndex - currentValueIndex
    val halfSize = valuesSize / 2

    if (delta > halfSize) delta -= valuesSize
    if (delta < -halfSize) delta += valuesSize

    return (currentIndex + delta).coerceIn(0, Int.MAX_VALUE - 1)
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private object BlockParentVerticalScrollConnection : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset = Offset(x = 0f, y = available.y)

    override suspend fun onPostFling(
        consumed: Velocity,
        available: Velocity,
    ): Velocity = Velocity(x = 0f, y = available.y)
}


@Preview
@Preview(name = "큰 글자", fontScale = 2f)
@Composable
private fun TimePickerWheelPreview() {
    RingoutTheme {
        var selected by remember { mutableStateOf(6) }
        TimePickerWheel(
            values = (1..12).toList(), selectedValue = selected, onValueChange = { selected = it },
            valueLabel = { it.toString().padStart(2, '0') }, accessibilityLabel = "시",
            selectedColor = alarmSetupColors().accent, unselectedColor = alarmSetupColors().secondaryText,
            selectedFontSize = 50.sp, unselectedFontSize = 42.sp, fontWeight = FontWeight.Bold,
            isLooping = true, hapticFeedbackType = HapticFeedbackType.SegmentFrequentTick,
            itemHeight = with(LocalDensity.current) { 50.sp.toDp() }.coerceAtLeast(70.dp),
        )
    }
}
