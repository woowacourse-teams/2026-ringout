package com.joon.ringout.presentation.alarmguide.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
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
    onNext: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    nextEnabled: Boolean = true,
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
    val canAdvance = nextEnabled && !hasCompletionState
    val currentCanAdvance by rememberUpdatedState(canAdvance)
    val currentOnNext by rememberUpdatedState(onNext)
    val nextActionLabel = if (step == AlarmCreationGuideStep.CreateAlarm) "가이드 마치기" else "다음 안내"

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .testTag("guide-overlay")
            .pointerInput(Unit) {
                detectGuideTaps(
                    isEnabled = { currentCanAdvance },
                    onTap = { currentOnNext() },
                )
            }
            .semantics {
                isTraversalGroup = true
                paneTitle = "알람 생성 가이드"
                role = Role.Button
                if (!canAdvance) disabled()
                onClick(label = nextActionLabel) {
                    if (currentCanAdvance) {
                        currentOnNext()
                        true
                    } else {
                        false
                    }
                }
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
        Canvas(Modifier.fillMaxSize()) {
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
                    .testTag("guide-target")
                    .semantics { contentDescription = step.targetDescription },
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

private suspend fun PointerInputScope.detectGuideTaps(
    isEnabled: () -> Boolean,
    onTap: () -> Unit,
) {
    awaitEachGesture {
        // Skip/retry consume their own down events. The overlay owns every other gesture,
        // so the underlying settings never join the pointer hit path.
        val down = awaitFirstDown(requireUnconsumed = true)
        down.consume()
        var isTap = isEnabled()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            val hasAnotherPointer = event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) }
            val movedPastSlop = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
            val outsideOverlay = change.position.x < 0 || change.position.x > size.width ||
                change.position.y < 0 || change.position.y > size.height
            if (hasAnotherPointer || movedPastSlop || outsideOverlay || change.isConsumed) isTap = false

            if (change.changedToUpIgnoreConsumed()) {
                // Consume release before dismissing, including the final guide tap.
                change.consume()
                if (isTap && isEnabled()) onTap()
                return@awaitEachGesture
            }

            // Leave movement to the annotation's scroll container, but never advance after
            // that child (or an ancestor) takes over a gesture.
            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
            if (finalEvent.changes.any { it.isConsumed }) isTap = false
        }
    }
}

private val AlarmCreationGuideStep.targetDescription: String
    get() = when (this) {
        AlarmCreationGuideStep.AddAlarm -> "알람 추가"
        AlarmCreationGuideStep.Time -> "알람 시간"
        AlarmCreationGuideStep.Weekdays -> "반복 요일"
        AlarmCreationGuideStep.Destination -> "목적지"
        AlarmCreationGuideStep.Interval -> "반복 간격"
        AlarmCreationGuideStep.Sound -> "알람음"
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
                onNext = {},
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
                onNext = {},
                onSkip = {},
            )
        }
    }
}

@Preview(name = "가이드 · 작은 화면 · 큰 글자 · 다크", widthDp = 320, heightDp = 568, fontScale = 2f)
@Composable
private fun AlarmCreationGuideOverlayCompactPreview() {
    RingoutTheme(ThemeMode.Dark) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AlarmCreationGuideOverlay(
                step = AlarmCreationGuideStep.CreateAlarm,
                targetBounds = with(LocalDensity.current) { Rect(20.dp.toPx(), 490.dp.toPx(), 300.dp.toPx(), 548.dp.toPx()) },
                onNext = {},
                onSkip = {},
            )
        }
    }
}

@Preview(name = "가이드 · 작은 화면 · 큰 글자 · 라이트", widthDp = 320, heightDp = 568, fontScale = 2f)
@Composable
private fun AlarmCreationGuideOverlayCompactLightPreview() {
    RingoutTheme(ThemeMode.Light) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            AlarmCreationGuideOverlay(
                step = AlarmCreationGuideStep.CreateAlarm,
                targetBounds = with(LocalDensity.current) { Rect(20.dp.toPx(), 490.dp.toPx(), 300.dp.toPx(), 548.dp.toPx()) },
                onNext = {},
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
