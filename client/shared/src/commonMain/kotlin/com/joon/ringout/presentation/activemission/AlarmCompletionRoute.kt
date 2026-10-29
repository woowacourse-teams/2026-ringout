package com.joon.ringout.presentation.activemission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.alarm.ActiveAlarmMission
import com.joon.ringout.alarm.ActiveAlarmMissionLocation
import com.joon.ringout.alarm.ForceEndMissionResult
import com.joon.ringout.presentation.activemission.component.AlarmCompletionWaiting
import com.joon.ringout.presentation.activemission.component.ForceEndMissionStatus

/** Allows only alarm completion while ordinary app entry is restricted by the platform. */
@Composable
fun AlarmCompletionRoute(
    activeMission: ActiveAlarmMission?,
    currentLocation: ActiveAlarmMissionLocation?,
    hasPendingAlarm: Boolean,
    onBackground: () -> Unit,
    onExpired: () -> Unit,
    onForceEnd: suspend (String) -> ForceEndMissionResult,
    onForceEndHoldStarted: (String) -> Unit,
    onForceEndHoldCancelled: (String, Long) -> Unit,
    onForceEndHoldCompleted: (String, Long) -> Unit,
    onReturnToRinging: (() -> Unit)?,
    blockedContent: @Composable () -> Unit,
) {
    // Same Activity-scoped instance as App: a live gate must not cancel an in-flight force end.
    val forceEndViewModel = viewModel { ForceEndMissionViewModel(createSavedStateHandle()) }
    val forceEndState by forceEndViewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(forceEndViewModel) {
        forceEndViewModel.resumePending(onForceEnd)
    }

    if (activeMission != null) {
        ActiveAlarmTrackingScreen(
            mission = activeMission,
            currentLocation = currentLocation,
            onBackClick = onBackground,
            onForceEndClick = { forceEndViewModel.request(it, onForceEnd) },
            onExpired = onExpired,
            isForceEndEnabled = forceEndState.phase == ForceEndPhase.Idle,
            onForceEndHoldStarted = onForceEndHoldStarted,
            onForceEndHoldCancelled = onForceEndHoldCancelled,
            onForceEndHoldCompleted = onForceEndHoldCompleted,
        )
    } else if (hasPendingAlarm || forceEndState.phase != ForceEndPhase.Idle) {
        AlarmCompletionWaiting(onBackground, onReturnToRinging)
    } else {
        blockedContent()
    }

    // Keep success acknowledgement and failure retry available even after the stored mission clears.
    ForceEndMissionStatus(
        uiState = forceEndState,
        onConfirm = forceEndViewModel::dismiss,
        onRetry = { forceEndViewModel.retry(onForceEnd) },
        onDismiss = forceEndViewModel::dismiss,
    )
}
