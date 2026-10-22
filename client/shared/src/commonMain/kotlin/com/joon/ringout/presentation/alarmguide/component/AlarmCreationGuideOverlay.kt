package com.joon.ringout.presentation.alarmguide.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarmGuideFontFamily
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideStep
import com.joon.ringout.ringoutColors
import kotlin.math.roundToInt

@Composable
internal fun AlarmCreationGuideOverlay(
    step: AlarmCreationGuideStep,
    targetBounds: Rect?,
    onTargetClick: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    targetEnabled: Boolean = true,
    isFinishing: Boolean = false,
    finishFailed: Boolean = false,
    onRetryFinish: () -> Unit = {},
) {
    val density = LocalDensity.current
    val statusBarHeight = WindowInsets.statusBars.getTop(density)
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density)
    val color = MaterialTheme.colorScheme.primary
    val scrim = if (step == AlarmCreationGuideStep.AddAlarm) {
        MaterialTheme.ringoutColors.alarmGuideHomeScrim
    } else {
        MaterialTheme.ringoutColors.alarmGuideScrim
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val hasCompletionState = isFinishing || finishFailed

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .testTag("guide-overlay")
            .semantics {
                isTraversalGroup = true
                paneTitle = "알람 생성 가이드"
            },
    ) {
        val bounds = targetBounds
            ?.translate(-origin)
            ?.intersect(Rect(Offset.Zero, constraints.let { androidx.compose.ui.geometry.Size(it.maxWidth.toFloat(), it.maxHeight.toFloat()) }))
            ?.takeIf { !it.isEmpty && !hasCompletionState }
        val radius = if (step == AlarmCreationGuideStep.AddAlarm && bounds != null) {
            minOf(bounds.width, bounds.height) / 2f
        } else {
            with(density) { 20.dp.toPx() }
        }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
        ) {
            val mask = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                bounds?.let { addRoundRect(RoundRect(it, CornerRadius(radius))) }
            }
            drawPath(mask, scrim)
            bounds?.let {
                drawRoundRect(
                    color = color,
                    topLeft = it.topLeft,
                    size = it.size,
                    cornerRadius = CornerRadius(radius),
                    style = Stroke(2.dp.toPx()),
                )
            }
        }

        if (bounds != null) {
            AlarmCreationGuideAnnotation(
                step = step,
                targetBounds = bounds,
                reservedTop = statusBarHeight + with(density) { 84.dp.roundToPx() },
                reservedBottom = navigationBarHeight + with(density) { 12.dp.roundToPx() },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                    .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .clip(if (step == AlarmCreationGuideStep.AddAlarm) CircleShape else RoundedCornerShape(20.dp))
                    .testTag("guide-target")
                    .semantics { contentDescription = step.targetActionDescription }
                    .clickable(
                        enabled = targetEnabled,
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTargetClick,
                    ),
            )
        }

        TextButton(
            onClick = onSkip,
            enabled = !hasCompletionState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = with(density) { statusBarHeight.toDp() } + 19.dp, end = 18.dp)
                .heightIn(min = 44.dp)
                .testTag("guide-skip"),
        ) {
            Text(
                text = "건너뛰기",
                color = color,
                fontFamily = MaterialTheme.alarmGuideFontFamily,
                fontSize = 22.sp,
                lineHeight = 26.sp,
            )
        }

        if (hasCompletionState) {
            AlarmCreationGuideCompletion(
                isFinishing = isFinishing,
                onRetryFinish = onRetryFinish,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
    }
}

private val AlarmCreationGuideStep.targetActionDescription: String
    get() = when (this) {
        AlarmCreationGuideStep.AddAlarm -> "알람 추가"
        AlarmCreationGuideStep.Time -> "시간 안내를 확인하고 요일 안내로 이동"
        AlarmCreationGuideStep.Weekdays -> "요일 안내를 확인하고 목적지 안내로 이동"
        AlarmCreationGuideStep.Destination -> "목적지 선택"
        AlarmCreationGuideStep.Interval -> "반복 간격 안내를 확인하고 다음 안내로 이동"
        AlarmCreationGuideStep.Sound -> "알람음 선택"
        AlarmCreationGuideStep.CreateAlarm -> "알람 생성"
    }

@Composable
private fun AlarmCreationGuideCompletion(
    isFinishing: Boolean,
    onRetryFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = if (isFinishing) "가이드를 마무리하고 있어요." else "가이드 종료를 저장하지 못했어요.",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (isFinishing) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            TextButton(onClick = onRetryFinish) { Text("다시 시도") }
        }
    }
}

@Preview(name = "가이드 · 다크", widthDp = 402, heightDp = 941)
@Composable
private fun AlarmCreationGuideOverlayPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AlarmCreationGuideOverlay(
                step = AlarmCreationGuideStep.AddAlarm,
                targetBounds = with(LocalDensity.current) { Rect(314.dp.toPx(), 702.dp.toPx(), 372.dp.toPx(), 760.dp.toPx()) },
                onTargetClick = {},
                onSkip = {},
            )
        }
    }
}

@Preview(name = "가이드 · 라이트", widthDp = 402, heightDp = 941)
@Composable
private fun AlarmCreationGuideOverlayLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AlarmCreationGuideOverlay(
                step = AlarmCreationGuideStep.Destination,
                targetBounds = with(LocalDensity.current) { Rect(20.dp.toPx(), 500.dp.toPx(), 382.dp.toPx(), 625.dp.toPx()) },
                onTargetClick = {},
                onSkip = {},
            )
        }
    }
}

@Preview
@Composable
private fun AlarmCreationGuideCompletionPreview() {
    RingoutTheme { AlarmCreationGuideCompletion(isFinishing = false, onRetryFinish = {}) }
}
