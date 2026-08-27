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

/** Remains composed when a priority screen covers the editor, as the old result handler did. */
@Composable
internal fun AlarmEditorNavigationEffects(
    navigation: AlarmEditorNavigation,
    screen: AppScreen,
) {
    val savedEvent = navigation.destinationViewModel.uiState.savedEvent
    LaunchedEffect(savedEvent?.eventId) {
        savedEvent?.let(navigation::onDestinationSaved)
    }
    LaunchedEffect(screen, navigation.hasValidDraft()) {
        navigation.onMissingDraft(screen)
    }
}

/** Shares one draft across the editor and its pickers; route keys contain identifiers only. */
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
        // A cancelled visit must not leave a result for the next visit to consume.
        destinationViewModel.consumeSavedEvent(event.eventId)
    }

    fun onMissingDraft(screen: AppScreen) {
        if (
            screen in AlarmEditorScreens &&
            navigationState.requestedScreen == screen && !hasValidDraft()
        ) {
            // Drafts are deliberately not serialized into the back stack.
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
