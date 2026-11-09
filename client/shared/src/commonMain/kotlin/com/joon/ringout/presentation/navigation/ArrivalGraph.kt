package com.joon.ringout.presentation.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.presentation.arrival.ArrivalRoute
import com.joon.ringout.presentation.arrival.ArrivalViewModel
import com.joon.ringout.presentation.arrival.PendingArrivalUiState

internal fun EntryProviderScope<AppRoute>.arrivalGraph(
    navigationState: AppNavigationState,
    repository: ArrivalResultRepository,
    pendingArrival: PendingArrivalUiState,
    hasActiveRuntime: Boolean,
) {
    entry<AppRoute.Arrival>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val arrivalViewModel = viewModel { ArrivalViewModel(route.occurrenceId, repository) }
        ArrivalRoute(
            viewModel = arrivalViewModel,
            onConfirmed = {
                completeArrivalNavigation(
                    navigationState = navigationState,
                    occurrenceId = route.occurrenceId,
                    pendingArrivalOccurrenceId = pendingArrival.result?.occurrenceId,
                    isArrivalStateLoaded = pendingArrival.isLoaded,
                    hasActiveRuntime = hasActiveRuntime,
                )
            },
        )
    }
}

/** An earlier confirmation must not replace a new mission, a newer receipt or another screen. */
internal fun completeArrivalNavigation(
    navigationState: AppNavigationState,
    occurrenceId: String,
    pendingArrivalOccurrenceId: String?,
    isArrivalStateLoaded: Boolean,
    hasActiveRuntime: Boolean,
) {
    if (!isArrivalStateLoaded || hasActiveRuntime || pendingArrivalOccurrenceId != null) return
    // The receipt observer may update after this callback. The runtime effect also clears the
    // arrival route when that update arrives, so an old pending snapshot cannot reopen it.
    if (navigationState.isCurrentRoute(AppRoute.Arrival(occurrenceId))) {
        navigationState.navigate(AppRoute.Home)
    }
}
