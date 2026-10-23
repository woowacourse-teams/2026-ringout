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
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.presentation.alarmguide.component.AlarmCreationGuideOverlay
import com.joon.ringout.presentation.alarmguide.component.LocalAlarmCreationGuideTargets
import com.joon.ringout.presentation.alarmguide.component.rememberAlarmCreationGuideTargets

@Composable
internal fun AlarmCreationGuideRoute(
    viewModel: AlarmCreationGuideViewModel,
    isPending: Boolean,
    surface: AlarmCreationGuideSurface,
    canPresent: Boolean,
    isAlarmSaving: Boolean,
    isFinishing: Boolean,
    finishFailed: Boolean,
    onOpenEditor: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val targets = rememberAlarmCreationGuideTargets()
    // Do not key this effect on the guide step: navigation can settle after the first guide tap.
    LaunchedEffect(isPending, surface, canPresent) {
        viewModel.onContextChanged(isPending, surface, canPresent)
    }
    val step = state.step
    val isGuideSurface = surface == AlarmCreationGuideSurface.Home || surface == AlarmCreationGuideSurface.Editor
    val visible = isPending && canPresent && isGuideSurface && step != null
    SideEffect { targets.activeStep = step.takeIf { visible && !state.isFinishing } }
    val finish = { if (!isAlarmSaving && !isFinishing && viewModel.finish()) onFinish() }

    Box(modifier.fillMaxSize()) {
        // Dimmed controls remain unchanged visually and cannot receive accessibility actions.
        Box(if (visible) Modifier.fillMaxSize().clearAndSetSemantics {} else Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalAlarmCreationGuideTargets provides targets.takeIf { isPending }) { content() }
        }
        if (visible) {
            val target = targets[step]
            val nextEnabled = !isAlarmSaving && !state.isFinishing && !isFinishing && target != null
            AlarmCreationGuideOverlay(
                step = step,
                targetBounds = target?.boundsInRoot,
                onNext = {
                    if (nextEnabled && viewModel.onNext(step)) {
                        when (step) {
                            AlarmCreationGuideStep.AddAlarm -> onOpenEditor()
                            AlarmCreationGuideStep.CreateAlarm -> onFinish()
                            else -> Unit
                        }
                    }
                },
                onSkip = finish,
                nextEnabled = nextEnabled,
                isFinishing = (state.isFinishing || isFinishing) && !finishFailed,
                finishFailed = finishFailed,
                onRetryFinish = onFinish,
            )
        }
    }
    AlarmCreationGuideBackHandler(
        visible = visible,
        isBusy = isAlarmSaving || state.isFinishing || isFinishing,
        onFinish = finish,
    )
}

@Composable
internal fun AlarmCreationGuideBackHandler(
    visible: Boolean,
    isBusy: Boolean,
    onFinish: () -> Unit,
) {
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = visible,
        onBackCompleted = {
            if (!isBusy) onFinish()
        },
    )
}
