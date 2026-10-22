package com.joon.ringout.presentation.alarmguide.component

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import com.joon.ringout.presentation.alarmguide.AlarmCreationGuideStep

internal data class AlarmCreationGuideTarget(
    val boundsInRoot: Rect,
    val onClick: (() -> Unit)?,
)

@Stable
internal class AlarmCreationGuideTargets {
    var activeStep: AlarmCreationGuideStep? by mutableStateOf(null)

    private val targets = mutableStateMapOf<AlarmCreationGuideStep, Registration>()

    operator fun get(step: AlarmCreationGuideStep): AlarmCreationGuideTarget? = targets[step]?.target

    internal fun update(step: AlarmCreationGuideStep, owner: Any, target: AlarmCreationGuideTarget) {
        targets[step] = Registration(owner, target)
    }

    internal fun remove(step: AlarmCreationGuideStep, owner: Any) {
        // Navigation transitions may temporarily compose two copies of the same target.
        if (targets[step]?.owner === owner) targets.remove(step)
    }

    private data class Registration(val owner: Any, val target: AlarmCreationGuideTarget)
}

internal val LocalAlarmCreationGuideTargets = staticCompositionLocalOf<AlarmCreationGuideTargets?> { null }

@Composable
internal fun rememberAlarmCreationGuideTargets(): AlarmCreationGuideTargets = remember { AlarmCreationGuideTargets() }

internal fun Modifier.alarmCreationGuideTarget(
    step: AlarmCreationGuideStep,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val targets = LocalAlarmCreationGuideTargets.current ?: return@composed this
    val owner = remember { Any() }
    val latestOnClick by rememberUpdatedState(onClick)
    val click = remember(onClick != null) {
        if (onClick == null) null else ({ latestOnClick?.invoke(); Unit })
    }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    DisposableEffect(targets, step, owner) {
        onDispose { targets.remove(step, owner) }
    }
    LaunchedEffect(targets.activeStep, step) {
        if (targets.activeStep == step) {
            withFrameNanos { }
            bringIntoViewRequester.bringIntoView()
        }
    }

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onGloballyPositioned { coordinates ->
            targets.update(
                step = step,
                owner = owner,
                target = AlarmCreationGuideTarget(
                    // Keep the full target bounds even while a scroll ancestor clips it.
                    boundsInRoot = Rect(coordinates.positionInRoot(), coordinates.size.toSize()),
                    onClick = click,
                ),
            )
        }
}
