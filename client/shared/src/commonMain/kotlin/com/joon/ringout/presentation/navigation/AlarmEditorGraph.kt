package com.joon.ringout.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.toAnalyticsLoginStateOrNull
import com.joon.ringout.presentation.destination.component.DestinationEntry
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.alarmsound.AlarmSoundRoute
import com.joon.ringout.presentation.alarmsetup.AlarmSetupRoute
import com.joon.ringout.presentation.destination.DestinationRoute
import com.joon.ringout.presentation.destination.DestinationManagementRoute

// 편집 흐름이 백스택에서 제거될 때까지 AlarmSetup과 Destination은 Add/Edit 부모 저장소를 공유한다.
internal fun EntryProviderScope<AppRoute>.alarmEditorGraph(
    navigation: AlarmEditorNavigation,
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
    productAnalyticsRecorder: ProductAnalyticsRecorder,
) {
    entry<AppRoute.AddAlarm>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        if (navigation.hasValidDraft()) {
            AlarmEditorDestinationEntry(
                navigation, route, displayedRoute, authSessionState, productAnalyticsRecorder,
            )
        }
    }

    entry<AppRoute.EditAlarm>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        if (navigation.hasValidDraft()) {
            AlarmEditorDestinationEntry(navigation, route, displayedRoute, authSessionState, productAnalyticsRecorder)
        }
    }

    entry<AppRoute.Destination>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        if (navigation.hasValidDraft()) {
            DestinationRoute(
                viewModel = navigation.destinationViewModel,
                initialSelection = navigation.alarmSetupViewModel.uiState.destination,
                requestId = route.requestId,
                authSessionState = authSessionState,
                productAnalyticsRecorder = productAnalyticsRecorder,
                isActive = navigation.isActive(route, displayedRoute),
                onBackClick = { navigation.onBack(route, displayedRoute) },
                onSavedDestinationConfirmClick = { destination ->
                    navigation.onSavedDestinationSelected(route, displayedRoute, destination)
                },
                onConfirmWithoutSave = { destination ->
                    navigation.onDestinationSelected(route, displayedRoute, destination)
                },
            )
        }
    }

    entry<AppRoute.DestinationManagement>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        if (navigation.hasValidDraft()) {
            DestinationManagementRoute(
                viewModel = navigation.destinationViewModel,
                isActive = navigation.isActive(route, displayedRoute),
                onFinished = { navigation.onBack(route, displayedRoute) },
            )
        }
    }

    entry<AppRoute.AlarmSound>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        if (navigation.hasValidDraft()) {
            AlarmSoundRoute(
                selectedSound = navigation.alarmSetupViewModel.uiState.alarmSound,
                isActive = navigation.isActive(route, displayedRoute),
                onBackClick = { navigation.onBack(route, displayedRoute) },
                onSaveClick = { sound, analyticsContext ->
                    if (navigation.isActive(AppRoute.AlarmSound, displayedRoute)) {
                        navigation.onAlarmSoundSelected(displayedRoute, sound, analyticsContext)
                    }
                },
            )
        }
    }
}

@Composable
private fun AlarmEditorDestinationEntry(
    navigation: AlarmEditorNavigation,
    route: AppRoute,
    displayedRoute: AppRoute,
    authSessionState: AuthSessionState,
    analytics: ProductAnalyticsRecorder,
) {
    val isActive = navigation.isActive(route, displayedRoute)
    DestinationEntry(
        uiState = navigation.destinationViewModel.uiState,
        isActive = isActive && !navigation.alarmSetupViewModel.uiState.isSaveInProgress,
        onFindInMap = { navigation.onDestinationClick(route, displayedRoute) },
        onSelect = { destination ->
            if (isActive) {
                authSessionState.toAnalyticsLoginStateOrNull()?.let { loginState ->
                    analytics.recordDestinationSelected(DestinationSelectionSource.SavedDestinationDialog, loginState)
                }
                navigation.onStoredDestinationSelected(route, displayedRoute, destination)
            }
        },
        onRetry = navigation.destinationViewModel::onScreenEntered,
        onManage = { navigation.onManageDestination(route, displayedRoute, it) },
        openRequestId = navigation.destinationDialogRequestId,
    ) { onDestinationClick ->
        AlarmSetupRoute(
            viewModel = navigation.alarmSetupViewModel,
            savedDestinations = navigation.destinationViewModel.uiState.let { state ->
                state.destinations.takeIf { state.hasLoadedDestinations }
            },
            isActive = isActive,
            onBackClick = { navigation.onBack(route, displayedRoute) },
            onDestinationClick = onDestinationClick,
            onAlarmSoundClick = { navigation.onAlarmSoundClick(route, displayedRoute) },
        )
    }
}
