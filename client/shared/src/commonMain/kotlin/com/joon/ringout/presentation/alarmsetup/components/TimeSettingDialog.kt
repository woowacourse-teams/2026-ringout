package com.joon.ringout.presentation.alarmsetup.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.joon.ringout.RingoutTheme
import kotlinx.coroutines.launch
import kotlin.math.abs

private val DialogPrimary = Color(0xFF161A17)
private val DialogSecondary = Color(0xFF6E756F)
private val DialogMuted = Color(0xFFB1B8B0)
private val DialogOrange = Color(0xFFFF6B2C)
private val DialogPale = Color(0xFFF7F8F5)

@Composable
fun TimeSettingDialog(
    initialTime: String,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initialHour24 = initialTime.substringBefore(":").toIntOrNull() ?: 6
    val initialMinute = initialTime.substringAfter(":").toIntOrNull() ?: 20
    var isAm by remember(initialTime) { mutableStateOf(initialHour24 < 12) }
    var hour by remember(initialTime) { mutableStateOf(initialHour24.toTwelveHour()) }
    var minute by remember(initialTime) { mutableStateOf(initialMinute.floorMod(60)) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        TimeSettingDialogContent(
            isAm = isAm,
            hour = hour,
            minute = minute,
            onAmPmChange = { isAm = it },
            onHourChange = { hour = it },
            onMinuteChange = { minute = it },
            onCancel = onDismissRequest,
            onClose = onDismissRequest,
            onConfirm = {
                val hour24 = when {
                    isAm && hour == 12 -> 0
                    !isAm && hour != 12 -> hour + 12
                    else -> hour
                }
                onConfirm("${hour24.twoDigits()}:${minute.twoDigits()}")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
    }
}

@Composable
fun TimeSettingDialogContent(
    isAm: Boolean,
    hour: Int,
    minute: Int,
    onAmPmChange: (Boolean) -> Unit,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "시간 설정",
                color = DialogPrimary,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
            )
            Box(
                Modifier.size(36.dp).background(DialogPale, RoundedCornerShape(12.dp)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) { CloseIcon() }
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).background(DialogPale, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AmPmSegment("오전", isAm, { onAmPmChange(true) }, Modifier.weight(1f))
            AmPmSegment("오후", !isAm, { onAmPmChange(false) }, Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth().height(184.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberPickerColumn(
                label = "시",
                values = HourValues,
                selected = hour,
                onValueChange = onHourChange,
                modifier = Modifier.weight(1f),
            )
            Text(":", color = DialogPrimary, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, fontWeight = FontWeight.Bold))
            NumberPickerColumn(
                label = "분",
                values = MinuteValues,
                selected = minute,
                onValueChange = onMinuteChange,
                modifier = Modifier.weight(1f),
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DialogButton("취소", DialogPale, DialogSecondary, onCancel, Modifier.weight(1f))
            DialogButton("설정 완료", DialogOrange, Color.White, onConfirm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AmPmSegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(36.dp).background(if (selected) DialogOrange else Color.Transparent, RoundedCornerShape(11.dp)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color.White else DialogSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun NumberPickerColumn(
    label: String,
    values: List<Int>,
    selected: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier,
) {
    val loopMiddle = remember(values) {
        val middle = Int.MAX_VALUE / 2
        middle - middle % values.size
    }
    val initialIndex = loopMiddle + values.indexOf(selected).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val coroutineScope = rememberCoroutineScope()
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentSelected by rememberUpdatedState(selected)

    LaunchedEffect(listState, values) {
        var lastCenteredIndex: Int? = null
        snapshotFlow { listState.centeredItemIndex() }.collect { centeredIndex ->
            if (centeredIndex != null && centeredIndex != lastCenteredIndex) {
                lastCenteredIndex = centeredIndex
                val value = values[centeredIndex.floorMod(values.size)]
                if (value != currentSelected) currentOnValueChange(value)
            }
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = DialogSecondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold))
        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxWidth().height(154.dp),
            contentPadding = PaddingValues(vertical = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(count = Int.MAX_VALUE) { index ->
                val centerFraction by remember(index) {
                    derivedStateOf { listState.centerFractionFor(index) }
                }
                WheelPickerValue(
                    value = values[index.floorMod(values.size)],
                    centerFraction = centerFraction,
                    onClick = {
                        coroutineScope.launch { listState.animateScrollToItem(index) }
                    },
                )
            }
        }
    }
}

@Composable
private fun WheelPickerValue(
    value: Int,
    centerFraction: Float,
    onClick: () -> Unit,
) {
    val itemScale = 0.76f + centerFraction * 0.24f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
                alpha = 0.58f + centerFraction * 0.42f
            }
            .background(
                Color(0xFFFFF0E8).copy(alpha = centerFraction * centerFraction),
                RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value.twoDigits(),
            color = lerp(DialogMuted, DialogOrange, centerFraction),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun DialogButton(label: String, background: Color, foreground: Color, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.height(50.dp).background(background, RoundedCornerShape(15.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = foreground, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
private fun CloseIcon() = Canvas(Modifier.size(20.dp)) {
    val stroke = 1.5.dp.toPx()
    drawLine(DialogSecondary, Offset(size.width * .3f, size.height * .3f), Offset(size.width * .7f, size.height * .7f), stroke, StrokeCap.Round)
    drawLine(DialogSecondary, Offset(size.width * .7f, size.height * .3f), Offset(size.width * .3f, size.height * .7f), stroke, StrokeCap.Round)
}

private fun Int.twoDigits() = toString().padStart(2, '0')
private fun Int.toTwelveHour() = when (val value = this % 12) { 0 -> 12 else -> value }
private fun Int.floorMod(divisor: Int) = ((this % divisor) + divisor) % divisor

private val HourValues = (1..12).toList()
private val MinuteValues = (0..59).toList()

private fun androidx.compose.foundation.lazy.LazyListState.centeredItemIndex(): Int? {
    val layoutInfo = layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    return layoutInfo.visibleItemsInfo.minByOrNull { item ->
        abs(item.offset + item.size / 2f - viewportCenter)
    }?.index
}

private fun androidx.compose.foundation.lazy.LazyListState.centerFractionFor(index: Int): Float {
    val layoutInfo = layoutInfo
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0f
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
    val distance = abs(item.offset + item.size / 2f - viewportCenter)
    return (1f - distance / item.size).coerceIn(0f, 1f)
}

@Preview
@Composable
private fun TimeSettingDialogContentPreview() {
    RingoutTheme {
        Box(Modifier.background(Color(0x99000000)).padding(24.dp)) {
            TimeSettingDialogContent(
                isAm = true,
                hour = 6,
                minute = 20,
                onAmPmChange = {},
                onHourChange = {},
                onMinuteChange = {},
                onCancel = {},
                onClose = {},
                onConfirm = {},
            )
        }
    }
}
