package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.push.RoomActivityPushRequest

/** 울림·활성 미션, 저장된 도착 결과, 모임 푸시 순으로 화면 이동을 조정한다. */
@Composable
internal fun ActiveMissionNavigationEffect(
    activeMissionOccurrenceId: String?,
    navigationState: AppNavigationState,
    authSessionState: AuthSessionState = AuthSessionState.Authenticated,
    pendingRoomActivityPush: RoomActivityPushRequest? = null,
    isRingingAlarm: Boolean = false,
    canHandleRoomActivityPush: Boolean = true,
    onRoomActivityPushHandled: (String) -> Unit = {},
    pendingArrivalOccurrenceId: String? = null,
    isArrivalStateLoaded: Boolean = true,
) {
    var handledOccurrenceId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    // 도착 결과는 확인 전까지 다시 표시하되, 같은 활성 미션에서 홈으로 나온 선택은 유지한다.
    LaunchedEffect(
        activeMissionOccurrenceId,
        navigationState,
        authSessionState,
        pendingRoomActivityPush?.requestId,
        isRingingAlarm,
        canHandleRoomActivityPush,
        pendingArrivalOccurrenceId,
        isArrivalStateLoaded,
        navigationState.requestedRoute as? AppRoute.Arrival,
    ) {
        if (isRingingAlarm) return@LaunchedEffect
        if (activeMissionOccurrenceId != null) {
            if (handledOccurrenceId != activeMissionOccurrenceId ||
                navigationState.requestedRoute is AppRoute.Arrival
            ) {
                handledOccurrenceId = activeMissionOccurrenceId
                navigationState.navigate(AppRoute.ActiveAlarmTracking(activeMissionOccurrenceId))
            }
            return@LaunchedEffect
        }

        handledOccurrenceId = null
        if (navigationState.requestedRoute is AppRoute.ActiveAlarmTracking) {
            navigationState.navigate(AppRoute.Home)
        }
        // 초기 null은 조회 결과가 아니다. 복원된 도착 화면과 미처리 푸시를 유지한다.
        if (!isArrivalStateLoaded) return@LaunchedEffect
        if (pendingArrivalOccurrenceId != null) {
            navigationState.navigate(AppRoute.Arrival(pendingArrivalOccurrenceId))
            return@LaunchedEffect
        }
        // 확인 저장의 DB 알림이 화면의 완료 콜백보다 먼저 도착해도 홈으로 정리한다.
        if (navigationState.requestedRoute is AppRoute.Arrival) {
            navigationState.navigate(AppRoute.Home)
            return@LaunchedEffect
        }

        if (!canHandleRoomActivityPush) return@LaunchedEffect
        val pushRequest = pendingRoomActivityPush ?: return@LaunchedEffect
        when (authSessionState) {
            AuthSessionState.Restoring -> return@LaunchedEffect
            AuthSessionState.Unauthenticated,
            AuthSessionState.ReauthenticationRequired,
            -> onRoomActivityPushHandled(pushRequest.requestId)

            AuthSessionState.Authenticated -> {
                if (navigationState.openRoomActivityFromPush(pushRequest.target)) {
                    onRoomActivityPushHandled(pushRequest.requestId)
                }
            }
        }
    }
}
