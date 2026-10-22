package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.push.RoomActivityPushRequest

/** 활성 미션 회차마다 추적 화면 이동을 한 번만 요청하고, 종료 시 복귀 경로를 정리한다. */
@Composable
internal fun ActiveMissionNavigationEffect(
    activeMissionOccurrenceId: String?,
    navigationState: AppNavigationState,
    authSessionState: AuthSessionState = AuthSessionState.Authenticated,
    pendingRoomActivityPush: RoomActivityPushRequest? = null,
    isRingingAlarm: Boolean = false,
    onRoomActivityPushHandled: (String) -> Unit = {},
) {
    var handledOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // 요청 화면을 키에 넣으면 같은 미션에서 홈으로 나온 사용자를 다시 강제로 이동시킨다.
    LaunchedEffect(
        activeMissionOccurrenceId,
        navigationState,
        authSessionState,
        pendingRoomActivityPush?.requestId,
        isRingingAlarm,
    ) {
        when {
            activeMissionOccurrenceId == null -> {
                handledOccurrenceId = null
                if (navigationState.backStack.lastOrNull() is AppRoute.ActiveAlarmTracking) {
                    navigationState.navigate(AppRoute.Home)
                }
            }

            handledOccurrenceId != activeMissionOccurrenceId -> {
                handledOccurrenceId = activeMissionOccurrenceId
                navigationState.navigate(AppRoute.ActiveAlarmTracking(activeMissionOccurrenceId))
            }
        }

        val pushRequest = pendingRoomActivityPush ?: return@LaunchedEffect
        when (authSessionState) {
            AuthSessionState.Restoring -> return@LaunchedEffect
            AuthSessionState.Unauthenticated,
            AuthSessionState.ReauthenticationRequired,
            -> onRoomActivityPushHandled(pushRequest.requestId)

            AuthSessionState.Authenticated -> {
                if (isRingingAlarm) return@LaunchedEffect
                if (navigationState.openRoomActivityFromPush(pushRequest.target)) {
                    onRoomActivityPushHandled(pushRequest.requestId)
                }
            }
        }
    }
}
