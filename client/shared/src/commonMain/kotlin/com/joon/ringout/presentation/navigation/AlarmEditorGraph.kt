package com.joon.ringout.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.AppScreen
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsound.AlarmSoundRoute
import com.joon.ringout.presentation.alarmsetup.AlarmSetupRoute
import com.joon.ringout.presentation.destination.DefaultDestinationSelection
import com.joon.ringout.presentation.destination.DestinationRoute

// 편집 흐름이 백스택에서 제거될 때까지 AlarmSetup과 Destination은 Add/Edit 부모 저장소를 공유한다.
internal fun EntryProviderScope<AppRoute>.alarmEditorGraph(
    navigation: AlarmEditorNavigation,
    screen: AppScreen,
    authSessionState: AuthSessionState,
    productAnalyticsRecorder: ProductAnalyticsRecorder,
) {
    entry<AppRoute.AddAlarm>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
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

    entry<AppRoute.EditAlarm>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
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

    entry<AppRoute.Destination>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
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

    entry<AppRoute.AlarmSound>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
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
