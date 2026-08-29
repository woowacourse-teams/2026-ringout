package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joon.ringout.AppScreen

/** 활성 미션 회차마다 추적 화면 이동을 한 번만 요청하고, 종료 시 복귀 경로를 정리한다. */
@Composable
internal fun ActiveMissionNavigationEffect(
    activeMissionOccurrenceId: String?,
    navigationState: AppNavigationState,
) {
    var handledOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // 요청 화면을 키에 넣으면 같은 미션에서 홈으로 나온 사용자를 다시 강제로 이동시킨다.
    LaunchedEffect(activeMissionOccurrenceId) {
        when {
            activeMissionOccurrenceId == null -> {
                handledOccurrenceId = null
                if (navigationState.requestedScreen == AppScreen.ActiveAlarmTracking) {
                    navigationState.navigate(AppScreen.Home)
                }
            }

            handledOccurrenceId != activeMissionOccurrenceId -> {
                handledOccurrenceId = activeMissionOccurrenceId
                navigationState.navigate(AppScreen.ActiveAlarmTracking)
            }
        }
    }
}
