package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.joon.ringout.AppScreen
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.destination.DestinationSavedEvent
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
    return remember(navigationState, alarmSetupViewModel, destinationViewModel, destinationRequestId) {
        AlarmEditorNavigation(
            navigationState,
            alarmSetupViewModel,
            destinationViewModel,
            destinationRequestId,
        )
    }
}

/** 기존 결과 처리 방식과 같이 우선 화면이 편집 화면을 가려도 컴포지션에 유지된다. */
@Composable
internal fun AlarmEditorNavigationEffects(
    navigation: AlarmEditorNavigation,
    screen: AppScreen,
) {
    val savedEvent = navigation.destinationViewModel.uiState.savedEvent
    LaunchedEffect(navigation, savedEvent?.eventId) {
        savedEvent?.let(navigation::onDestinationSaved)
    }
    LaunchedEffect(navigation, screen, navigation.hasValidDraft()) {
        navigation.onMissingDraft(screen)
    }
}

/** 편집 화면과 선택 화면은 하나의 초안을 공유하며, 경로 키에는 식별자만 담는다. */
internal class AlarmEditorNavigation(
    private val navigationState: AppNavigationState,
    val alarmSetupViewModel: AlarmSetupViewModel,
    val destinationViewModel: DestinationViewModel,
    destinationRequestId: MutableState<Long> = mutableStateOf(0L),
) {
    private var destinationRequestId by destinationRequestId

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

    fun hasValidDraft(): Boolean = alarmSetupViewModel.hasDraft &&
        when (val parent = navigationState.editorRoute) {
            AppRoute.AddAlarm -> !alarmSetupViewModel.uiState.isEditing
            is AppRoute.EditAlarm -> alarmSetupViewModel.uiState.alarmId == parent.alarmId
            else -> false
        }

    fun isActive(route: AppRoute, screen: AppScreen): Boolean =
        hasValidDraft() && navigationState.isCurrentRoute(route) &&
            navigationState.requestedScreen == screen

    fun isBackBlocked(screen: AppScreen): Boolean = when (screen) {
        AppScreen.AddAlarm, AppScreen.EditAlarm -> alarmSetupViewModel.uiState.isSaveInProgress
        else -> false
    }

    fun onBack(route: AppRoute, screen: AppScreen) {
        if (!isActive(route, screen) || isBackBlocked(screen)) return
        navigationState.popBackStack(route)
    }

    fun onDestinationClick(from: AppRoute, screen: AppScreen) {
        if (from != navigationState.editorRoute || !isActive(from, screen)) return
        destinationRequestId = maxOf(
            destinationRequestId,
            destinationViewModel.uiState.savedEvent?.requestId ?: 0L,
        ) + 1L
        navigationState.navigate(AppRoute.Destination(destinationRequestId))
    }

    fun onAlarmSoundClick(from: AppRoute, screen: AppScreen) {
        if (from != navigationState.editorRoute || !isActive(from, screen)) return
        navigationState.navigate(AppRoute.AlarmSound)
    }

    fun onSavedDestinationSelected(
        route: AppRoute.Destination,
        screen: AppScreen,
        destination: SavedDestination,
    ) {
        if (!isActive(route, screen)) return
        alarmSetupViewModel.updateDestination(destination.toDestinationSelection())
        navigationState.popBackStack(route)
    }

    fun onAlarmSoundSelected(screen: AppScreen, sound: AlarmSoundSelection) {
        if (!isActive(AppRoute.AlarmSound, screen)) return
        alarmSetupViewModel.updateAlarmSound(sound)
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

    fun onMissingDraft(screen: AppScreen) {
        if (
            screen in AlarmEditorScreens &&
            navigationState.requestedScreen == screen && !hasValidDraft()
        ) {
            // 초안은 의도적으로 백스택에 직렬화하지 않는다.
            navigationState.navigate(AppRoute.Home)
        }
    }
}

private val AlarmEditorScreens = setOf(
    AppScreen.AddAlarm,
    AppScreen.EditAlarm,
    AppScreen.Destination,
    AppScreen.AlarmSound,
)
