package com.joon.ringout.presentation.activemission.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.joon.ringout.RingoutTheme
import kotlin.time.TimeMark
import kotlin.time.TimeSource

internal const val ForceEndHoldDurationSeconds = 30
internal const val ForceEndHoldDurationMillis = ForceEndHoldDurationSeconds * 1_000L
private val ForceEndButtonWidth = 160.dp
private val ForceEndButtonHeight = 48.dp
private val ForceEndActivationKeys = setOf(
    Key.Enter,
    Key.NumPadEnter,
    Key.Spacebar,
    Key.DirectionCenter,
)

private data class ForceEndKeyboardHoldOwner(
    val attemptId: Long,
    val key: Key,
)

@Composable
internal fun ForceEndHoldButton(
    onHoldStarted: () -> Unit,
    onHoldCancelled: (holdDurationMillis: Long) -> Unit,
    onHoldComplete: (holdDurationMillis: Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = activeAlarmTrackingColors()
    val lifecycleOwner = LocalLifecycleOwner.current
    val progress = remember { mutableStateOf(0f) }
    val elapsedMillis = remember { mutableStateOf(0L) }
    val activeAttemptId = remember { mutableStateOf<Long?>(null) }
    val keyboardHoldOwner = remember { mutableStateOf<ForceEndKeyboardHoldOwner?>(null) }
    val holdStartedAt = remember { mutableStateOf<TimeMark?>(null) }
    val interactionState = remember { ForceEndHoldInteractionState() }
    val currentOnHoldStarted by rememberUpdatedState(onHoldStarted)
    val currentOnHoldCancelled by rememberUpdatedState(onHoldCancelled)
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)
    val currentEnabled by rememberUpdatedState(enabled)
    val isHolding = activeAttemptId.value != null && enabled

    fun startHold(): Long? {
        if (!currentEnabled) return null
        val attemptId = interactionState.start() ?: return null
        holdStartedAt.value = TimeSource.Monotonic.markNow()
        elapsedMillis.value = 0L
        progress.value = 0f
        activeAttemptId.value = attemptId
        currentOnHoldStarted()
        return attemptId
    }

    fun measuredDurationMillis(attemptId: Long): Long {
        if (activeAttemptId.value != attemptId) return 0L
        return holdStartedAt.value
            ?.elapsedNow()
            ?.inWholeMilliseconds
            ?: elapsedMillis.value
    }

    fun dispatchResult(
        attemptId: Long,
        result: ForceEndHoldResult?,
    ): Boolean {
        result ?: return false
        if (activeAttemptId.value != attemptId) return false

        if (keyboardHoldOwner.value?.attemptId == attemptId) {
            keyboardHoldOwner.value = null
        }
        activeAttemptId.value = null
        holdStartedAt.value = null
        elapsedMillis.value = 0L
        progress.value = 0f
        when (result) {
            is ForceEndHoldResult.Cancelled ->
                currentOnHoldCancelled(result.holdDurationMillis)
            is ForceEndHoldResult.Completed ->
                currentOnHoldComplete(result.holdDurationMillis)
        }
        return true
    }

    fun finishHold(attemptId: Long): Boolean {
        return dispatchResult(
            attemptId = attemptId,
            result = interactionState.finish(
                attemptId = attemptId,
                elapsedMillis = measuredDurationMillis(attemptId),
            ),
        )
    }

    fun cancelHold(attemptId: Long): Boolean = dispatchResult(
        attemptId = attemptId,
        result = interactionState.cancel(
            attemptId = attemptId,
            elapsedMillis = measuredDurationMillis(attemptId),
        ),
    )

    DisposableEffect(lifecycleOwner, interactionState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                activeAttemptId.value?.let(::cancelHold)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activeAttemptId.value?.let(::cancelHold)
        }
    }

    LaunchedEffect(activeAttemptId.value, enabled) {
        val attemptId = activeAttemptId.value
        if (attemptId == null) {
            progress.value = 0f
            elapsedMillis.value = 0L
            return@LaunchedEffect
        }
        if (!enabled) {
            cancelHold(attemptId)
            return@LaunchedEffect
        }

        while (activeAttemptId.value == attemptId && currentEnabled) {
            val currentElapsedMillis = measuredDurationMillis(attemptId)
            elapsedMillis.value = currentElapsedMillis
            progress.value = forceEndHoldProgress(
                elapsedMillis = currentElapsedMillis,
                isHolding = true,
            )
            if (currentElapsedMillis >= ForceEndHoldDurationMillis) {
                finishHold(attemptId)
                return@LaunchedEffect
            }
            withFrameNanos {}
        }
        cancelHold(attemptId)
    }

    val remainingSeconds = forceEndHoldRemainingSeconds(elapsedMillis.value)
        .coerceAtLeast(1)
    val label = if (isHolding) {
        "${remainingSeconds}초 더 눌러주세요.."
    } else {
        "강제 종료"
    }
    val textStyle = MaterialTheme.typography.labelLarge.copy(
        fontSize = if (isHolding) 12.sp else 14.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Bold,
    )
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .width(ForceEndButtonWidth)
            .height(ForceEndButtonHeight)
            .clip(shape)
            .background(colors.panelContent)
            .onKeyEvent { event ->
                if (!enabled || event.key !in ForceEndActivationKeys) {
                    return@onKeyEvent false
                }
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (keyboardHoldOwner.value == null) {
                            startHold()?.let { attemptId ->
                                keyboardHoldOwner.value = ForceEndKeyboardHoldOwner(
                                    attemptId = attemptId,
                                    key = event.key,
                                )
                            }
                        }
                        true
                    }
                    KeyEventType.KeyUp -> {
                        keyboardHoldOwner.value
                            ?.takeIf { owner -> owner.key == event.key }
                            ?.let { owner ->
                                finishHold(owner.attemptId)
                                keyboardHoldOwner.value = null
                            }
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged { focusState ->
                if (!focusState.isFocused) {
                    keyboardHoldOwner.value?.let { owner ->
                        cancelHold(owner.attemptId)
                        keyboardHoldOwner.value = null
                    }
                }
            }
            .focusable(enabled = enabled)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "활성 알람 강제 종료"
                stateDescription = when {
                    !enabled -> "사용할 수 없음"
                    isHolding -> "${remainingSeconds}초 남음"
                    else -> "30초 동안 계속 누르기"
                }
                if (isHolding) {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = progress.value,
                        range = 0f..1f,
                    )
                }
                if (enabled) {
                    onClick(
                        label = if (isHolding) {
                            "강제 종료 누르기 취소"
                        } else {
                            "30초 강제 종료 누르기 시작"
                        },
                    ) {
                        val attemptId = activeAttemptId.value
                        if (attemptId != null) {
                            finishHold(attemptId)
                        } else {
                            startHold()
                        }
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = press@{
                        val attemptId = startHold() ?: return@press
                        try {
                            if (tryAwaitRelease()) {
                                finishHold(attemptId)
                            } else {
                                cancelHold(attemptId)
                            }
                        } finally {
                            cancelHold(attemptId)
                        }
                    },
                    onTap = {},
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.value)
                .align(Alignment.CenterStart)
                .background(colors.forceEndProgressFill),
        )

        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            color = colors.screenChrome,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
            style = textStyle,
        )
    }
}

internal fun forceEndHoldRemainingSeconds(
    elapsedMillis: Long,
    durationSeconds: Int = ForceEndHoldDurationSeconds,
): Int {
    require(durationSeconds > 0)
    val durationMillis = durationSeconds.toLong() * 1_000L
    val remainingMillis = (durationMillis - elapsedMillis.coerceAtLeast(0L))
        .coerceAtLeast(0L)
    return ((remainingMillis + 999L) / 1_000L).toInt()
}

internal fun forceEndHoldProgress(
    elapsedMillis: Long,
    isHolding: Boolean,
    durationMillis: Long = ForceEndHoldDurationMillis,
): Float {
    require(durationMillis > 0L)
    if (!isHolding) return 0f
    return (elapsedMillis.coerceAtLeast(0L).toDouble() / durationMillis)
        .toFloat()
        .coerceIn(0f, 1f)
}

internal class ForceEndHoldInteractionState(
    private val durationMillis: Long = ForceEndHoldDurationMillis,
) {
    private var nextAttemptId = 0L
    private var activeAttemptId: Long? = null

    init {
        require(durationMillis > 0L)
    }

    fun start(): Long? {
        if (activeAttemptId != null) return null
        nextAttemptId += 1L
        return nextAttemptId.also { attemptId ->
            activeAttemptId = attemptId
        }
    }

    fun finish(
        attemptId: Long,
        elapsedMillis: Long,
    ): ForceEndHoldResult? = end(
        attemptId = attemptId,
        elapsedMillis = elapsedMillis,
        forceCancelled = false,
    )

    fun cancel(
        attemptId: Long,
        elapsedMillis: Long,
    ): ForceEndHoldResult? = end(
        attemptId = attemptId,
        elapsedMillis = elapsedMillis,
        forceCancelled = true,
    )

    private fun end(
        attemptId: Long,
        elapsedMillis: Long,
        forceCancelled: Boolean,
    ): ForceEndHoldResult? {
        if (activeAttemptId != attemptId) return null
        activeAttemptId = null
        val normalizedDurationMillis = elapsedMillis.coerceAtLeast(0L)
        return if (!forceCancelled && normalizedDurationMillis >= durationMillis) {
            ForceEndHoldResult.Completed(normalizedDurationMillis)
        } else {
            ForceEndHoldResult.Cancelled(normalizedDurationMillis)
        }
    }
}

internal sealed interface ForceEndHoldResult {
    val holdDurationMillis: Long

    data class Cancelled(
        override val holdDurationMillis: Long,
    ) : ForceEndHoldResult

    data class Completed(
        override val holdDurationMillis: Long,
    ) : ForceEndHoldResult
}

@Preview(widthDp = 200, heightDp = 80)
@Composable
private fun ForceEndHoldButtonPreview() {
    RingoutTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            ForceEndHoldButton(
                onHoldStarted = {},
                onHoldCancelled = {},
                onHoldComplete = {},
            )
        }
    }
}
