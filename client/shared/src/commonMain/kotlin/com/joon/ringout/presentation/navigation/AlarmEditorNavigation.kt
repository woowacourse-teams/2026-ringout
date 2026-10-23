package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.analytics.AlarmSettingsAnalyticsContext
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.destination.DestinationSavedEvent
import com.joon.ringout.presentation.destination.DestinationSelection
import com.joon.ringout.presentation.destination.DestinationViewModel
import com.joon.ringout.presentation.destination.belongsToDestinationRequest
import com.joon.ringout.presentation.destination.toDestinationSelection

@Composable
internal fun rememberAlarmEditorNavigation(
    navigationState: AppNavigationState,
    alarmSetupViewModel: AlarmSetupViewModel,
    destinationViewModel: DestinationViewModel,
): AlarmEditorNavigation {
    val destinationRequestId = rememberSaveable { mutableStateOf(0L) }
    val dialogRequestId = rememberSaveable { mutableStateOf(0L) }
    return remember(navigationState, alarmSetupViewModel, destinationViewModel, destinationRequestId, dialogRequestId) {
        AlarmEditorNavigation(
            navigationState,
            alarmSetupViewModel,
            destinationViewModel,
            destinationRequestId,
            dialogRequestId,
        )
    }
}

/** 기존 결과 처리 방식과 같이 우선 화면이 편집 화면을 가려도 컴포지션에 유지된다. */
@Composable
internal fun AlarmEditorNavigationBinding(
    navigation: AlarmEditorNavigation,
    displayedRoute: AppRoute,
) {
    val savedEvent = navigation.destinationViewModel.uiState.savedEvent
    LaunchedEffect(navigation, savedEvent?.eventId) {
        savedEvent?.let(navigation::onDestinationSaved)
    }
    LaunchedEffect(navigation, displayedRoute, navigation.hasValidDraft()) {
        navigation.onMissingDraft(displayedRoute)
    }
}

/** 편집 화면과 선택 화면은 하나의 초안을 공유하며, 경로 키에는 식별자만 담는다. */
internal class AlarmEditorNavigation(
    private val navigationState: AppNavigationState,
    val alarmSetupViewModel: AlarmSetupViewModel,
    val destinationViewModel: DestinationViewModel,
    destinationRequestId: MutableState<Long> = mutableStateOf(0L),
    dialogRequestId: MutableState<Long> = mutableStateOf(0L),
) {
    var destinationDialogRequestId by dialogRequestId
        private set

    private var destinationRequestId by destinationRequestId

    val editorRoute: AppRoute?
        get() = navigationState.editorRoute

    init {
        this.destinationRequestId = maxOf(
            this.destinationRequestId,
            navigationState.backStack.filterIsInstance<AppRoute.Destination>()
                .maxOfOrNull(AppRoute.Destination::requestId) ?: 0L,
        )
    }

    fun startCreating(initialTime: String) {
        alarmSetupViewModel.startCreating(initialTime)
        navigationState.navigate(AppRoute.AddAlarm)
    }

    fun startEditing(request: AlarmScheduleRequest) {
        alarmSetupViewModel.startEditing(request)
        navigationState.navigate(AppRoute.EditAlarm(request.id))
    }

    // 퇴장 중인 Add/Edit 항목은 백스택에서 제거된 뒤에도 자신의 초안을 그릴 수 있다.
    // 이벤트 처리는 기본값인 현재 부모 경로로 계속 검증한다.
    fun hasValidDraft(parent: AppRoute? = editorRoute): Boolean = alarmSetupViewModel.hasDraft &&
        when (parent) {
            AppRoute.AddAlarm -> !alarmSetupViewModel.uiState.isEditing
            is AppRoute.EditAlarm -> alarmSetupViewModel.uiState.alarmId == parent.alarmId
            else -> false
        }

    fun isActive(route: AppRoute, displayedRoute: AppRoute): Boolean =
        hasValidDraft() && navigationState.isCurrentRoute(route) &&
            displayedRoute == route

    fun isBackBlocked(displayedRoute: AppRoute): Boolean = when (displayedRoute) {
        AppRoute.AddAlarm, is AppRoute.EditAlarm -> alarmSetupViewModel.uiState.isSaveInProgress
        is AppRoute.DestinationManagement -> destinationViewModel.uiState.isSaving
        else -> false
    }

    fun onBack(route: AppRoute, displayedRoute: AppRoute) {
        if (!isActive(route, displayedRoute) || isBackBlocked(displayedRoute)) return
        if (route is AppRoute.DestinationManagement) {
            val destinationState = destinationViewModel.uiState
            if (destinationState.destinations.isNotEmpty()) {
                destinationDialogRequestId += 1
            }
            destinationViewModel.closeManagement()
        }
        navigationState.popBackStack(route)
    }

    fun onManageDestination(from: AppRoute, displayedRoute: AppRoute, destination: SavedDestination) {
        if (from != navigationState.editorRoute || !isActive(from, displayedRoute)) return
        if (alarmSetupViewModel.uiState.isSaveInProgress) return
        if (destinationViewModel.beginManagement(destination.id)) {
            navigationState.navigate(AppRoute.DestinationManagement(destination.id))
        }
    }

    fun onDestinationClick(from: AppRoute, displayedRoute: AppRoute) {
        if (from != navigationState.editorRoute || !isActive(from, displayedRoute)) return
        destinationRequestId = maxOf(
            destinationRequestId,
            destinationViewModel.uiState.savedEvent?.requestId ?: 0L,
        ) + 1L
        navigationState.navigate(AppRoute.Destination(destinationRequestId))
    }

    fun onStoredDestinationSelected(
        from: AppRoute,
        displayedRoute: AppRoute,
        destination: SavedDestination,
    ) {
        if (from != navigationState.editorRoute || !isActive(from, displayedRoute)) return
        if (alarmSetupViewModel.uiState.isSaveInProgress) return
        alarmSetupViewModel.updateDestination(destination.toDestinationSelection())
    }

    fun onAlarmSoundClick(from: AppRoute, displayedRoute: AppRoute) {
        if (from != navigationState.editorRoute || !isActive(from, displayedRoute)) return
        navigationState.navigate(AppRoute.AlarmSound)
    }

    fun onSavedDestinationSelected(
        route: AppRoute.Destination,
        displayedRoute: AppRoute,
        destination: SavedDestination,
    ) {
        onDestinationSelected(route, displayedRoute, destination.toDestinationSelection())
    }

    fun onDestinationSelected(
        route: AppRoute.Destination,
        displayedRoute: AppRoute,
        destination: DestinationSelection,
    ) {
        if (!isActive(route, displayedRoute)) return
        alarmSetupViewModel.updateDestination(destination)
        navigationState.popBackStack(route)
    }

    fun onAlarmSoundSelected(
        displayedRoute: AppRoute,
        sound: AlarmSoundSelection,
        analyticsContext: AlarmSettingsAnalyticsContext = AlarmSettingsAnalyticsContext(),
    ) {
        if (!isActive(AppRoute.AlarmSound, displayedRoute)) return
        alarmSetupViewModel.updateAlarmSound(sound)
        alarmSetupViewModel.updateAlarmSoundAnalyticsContext(analyticsContext)
        navigationState.popBackStack(AppRoute.AlarmSound)
    }

    fun onDestinationSaved(event: DestinationSavedEvent) {
        val route = navigationState.backStack.last() as? AppRoute.Destination
        if (
            route != null && hasValidDraft() &&
            event.belongsToDestinationRequest(
                currentRequestId = route.requestId,
                isDestinationScreenVisible = navigationState.isCurrentRoute(route),
            )
        ) {
            alarmSetupViewModel.updateDestination(event.destination.toDestinationSelection())
            navigationState.popBackStack(route)
        }
        // 취소된 화면의 결과가 다음 진입 시 처리되지 않도록 소비한다.
        destinationViewModel.consumeSavedEvent(event.eventId)
    }

    fun onMissingDraft(displayedRoute: AppRoute) {
        if (
            displayedRoute.isAlarmEditorRoute() &&
            navigationState.requestedRoute == displayedRoute &&
            !hasValidDraft()
        ) {
            // 초안은 의도적으로 백스택에 직렬화하지 않는다.
            navigationState.navigate(AppRoute.Home)
        }
    }
}

private fun AppRoute.isAlarmEditorRoute(): Boolean = when (this) {
    AppRoute.AddAlarm,
    AppRoute.AlarmSound,
    is AppRoute.EditAlarm,
    is AppRoute.Destination,
    is AppRoute.DestinationManagement,
    -> true
    else -> false
}
