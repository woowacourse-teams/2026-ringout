package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.AppScreen
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsound.AlarmSoundRoute
import com.joon.ringout.presentation.alarmsetup.AlarmSetupRoute
import com.joon.ringout.presentation.destination.DefaultDestinationSelection
import com.joon.ringout.presentation.destination.DestinationRoute

// The root still uses these shared ViewModels for permission, save, and session effects.
// Their owner can move to the editor scope in the dedicated ViewModel-scope migration.
internal fun EntryProviderScope<AppRoute>.alarmEditorGraph(
    navigation: AlarmEditorNavigation,
    screen: AppScreen,
    authSessionState: AuthSessionState,
    productAnalyticsRecorder: ProductAnalyticsRecorder,
) {
    entry<AppRoute.AddAlarm> { route ->
        if (navigation.hasValidDraft()) {
            AlarmSetupRoute(
                viewModel = navigation.alarmSetupViewModel,
                isActive = navigation.isActive(route, screen),
                onBackClick = { navigation.onBack(route, screen) },
                onDestinationClick = { navigation.onDestinationClick(route, screen) },
                onAlarmSoundClick = { navigation.onAlarmSoundClick(route, screen) },
            )
        }
    }

    entry<AppRoute.EditAlarm> { route ->
        if (navigation.hasValidDraft()) {
            AlarmSetupRoute(
                viewModel = navigation.alarmSetupViewModel,
                isActive = navigation.isActive(route, screen),
                onBackClick = { navigation.onBack(route, screen) },
                onDestinationClick = { navigation.onDestinationClick(route, screen) },
                onAlarmSoundClick = { navigation.onAlarmSoundClick(route, screen) },
            )
        }
    }

    entry<AppRoute.Destination> { route ->
        if (navigation.hasValidDraft()) {
            DestinationRoute(
                viewModel = navigation.destinationViewModel,
                initialSelection = navigation.alarmSetupViewModel.uiState.destination
                    ?: DefaultDestinationSelection,
                requestId = route.requestId,
                authSessionState = authSessionState,
                productAnalyticsRecorder = productAnalyticsRecorder,
                isActive = navigation.isActive(route, screen),
                onBackClick = { navigation.onBack(route, screen) },
                onSavedDestinationConfirmClick = { destination ->
                    navigation.onSavedDestinationSelected(route, screen, destination)
                },
            )
        }
    }

    entry<AppRoute.AlarmSound> { route ->
        if (navigation.hasValidDraft()) {
            AlarmSoundRoute(
                selectedSound = navigation.alarmSetupViewModel.uiState.alarmSound,
                isActive = navigation.isActive(route, screen),
                onBackClick = { navigation.onBack(route, screen) },
                onSaveClick = { sound -> navigation.onAlarmSoundSelected(screen, sound) },
            )
        }
    }
}
