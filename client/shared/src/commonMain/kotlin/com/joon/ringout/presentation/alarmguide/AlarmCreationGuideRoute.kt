package com.joon.ringout.presentation.alarmguide

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.presentation.alarmguide.component.AlarmCreationGuideOverlay
import com.joon.ringout.presentation.alarmguide.component.LocalAlarmCreationGuideTargets
import com.joon.ringout.presentation.alarmguide.component.rememberAlarmCreationGuideTargets
import com.joon.ringout.presentation.destination.PlatformBackHandler

@Composable
internal fun AlarmCreationGuideRoute(
    viewModel: AlarmCreationGuideViewModel,
    isPending: Boolean,
    surface: AlarmCreationGuideSurface,
    canPresent: Boolean,
    hasDestination: Boolean,
    isAlarmSaving: Boolean,
    isFinishing: Boolean,
    finishFailed: Boolean,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val targets = rememberAlarmCreationGuideTargets()
    // Do not key this effect on the guide step: navigation can settle after the target tap.
    LaunchedEffect(isPending, surface, canPresent, hasDestination) {
        viewModel.onContextChanged(isPending, surface, canPresent, hasDestination)
    }
    val step = state.step
    val isGuideSurface = surface == AlarmCreationGuideSurface.Home || surface == AlarmCreationGuideSurface.Editor
    val visible = isPending && canPresent && isGuideSurface && step != null &&
        (state.isFinishing || state.selection == null)
    SideEffect { targets.activeStep = step.takeIf { visible && !state.isFinishing } }
    val finish = { if (viewModel.finish()) onFinish() }

    Box(modifier.fillMaxSize()) {
        // Dimmed controls remain unchanged visually and cannot receive accessibility actions.
        Box(if (visible) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalAlarmCreationGuideTargets provides targets.takeIf { isPending }) { content() }
        }
        if (visible) {
            val target = targets[step]
            val targetEnabled = !isAlarmSaving && !state.isFinishing &&
                (step != AlarmCreationGuideStep.CreateAlarm || hasDestination)
            AlarmCreationGuideOverlay(
                step = step,
                targetBounds = target?.boundsInRoot,
                onTargetClick = {
                    if (targetEnabled && target != null && viewModel.onTargetPressed(step)) {
                        target.onClick?.invoke()
                    }
                },
                onSkip = finish,
                targetEnabled = targetEnabled && target != null,
                isFinishing = (state.isFinishing || isFinishing) && !finishFailed,
                finishFailed = finishFailed,
                onRetryFinish = onFinish,
            )
        }
    }
    PlatformBackHandler(enabled = visible) {
        if (!isAlarmSaving && !state.isFinishing) finish()
    }
}
