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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.joon.ringout.RingoutTheme
import kotlin.time.TimeSource

internal const val ForceEndHoldDurationSeconds = 30
internal const val ForceEndHoldDurationMillis = ForceEndHoldDurationSeconds * 1_000L
private val ForceEndButtonWidth = 160.dp
private val ForceEndButtonHeight = 48.dp

@Composable
internal fun ForceEndHoldButton(
    onHoldComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = activeAlarmTrackingColors()
    val progress = remember { mutableStateOf(0f) }
    val elapsedMillis = remember { mutableStateOf(0L) }
    val holdActive = remember { mutableStateOf(false) }
    val completionGate = remember { ForceEndHoldCompletionGate() }
    val currentOnHoldComplete by rememberUpdatedState(onHoldComplete)
    val currentEnabled by rememberUpdatedState(enabled)
    val isHolding = holdActive.value && enabled

    LaunchedEffect(isHolding, enabled) {
        if (!isHolding || !enabled) {
            progress.value = 0f
            elapsedMillis.value = 0L
            completionGate.reset()
            if (!enabled) holdActive.value = false
            return@LaunchedEffect
        }

        completionGate.reset()
        val startedAt = TimeSource.Monotonic.markNow()
        while (holdActive.value && currentEnabled) {
            val currentElapsedMillis = startedAt.elapsedNow().inWholeMilliseconds
            elapsedMillis.value = currentElapsedMillis
            progress.value = forceEndHoldProgress(
                elapsedMillis = currentElapsedMillis,
                isHolding = true,
            )
            if (
                completionGate.shouldComplete(
                    elapsedMillis = currentElapsedMillis,
                    isHolding = holdActive.value && currentEnabled,
                )
            ) {
                holdActive.value = false
                currentOnHoldComplete()
                return@LaunchedEffect
            }
            withFrameNanos {}
        }
        progress.value = 0f
        elapsedMillis.value = 0L
        completionGate.reset()
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
                        holdActive.value = !holdActive.value
                        true
                    }
                } else {
                    disabled()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        holdActive.value = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            holdActive.value = false
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

internal class ForceEndHoldCompletionGate(
    private val durationMillis: Long = ForceEndHoldDurationMillis,
) {
    private var completionDispatched = false

    init {
        require(durationMillis > 0L)
    }

    fun reset() {
        completionDispatched = false
    }

    fun shouldComplete(
        elapsedMillis: Long,
        isHolding: Boolean,
    ): Boolean {
        if (!isHolding) {
            reset()
            return false
        }
        if (completionDispatched || elapsedMillis < durationMillis) return false
        completionDispatched = true
        return true
    }
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
            ForceEndHoldButton(onHoldComplete = {})
        }
    }
}
