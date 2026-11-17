package com.joon.ringout.presentation.navigation

import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.joon.ringout.presentation.arrivalshare.ArrivalShareRoute
import com.joon.ringout.presentation.arrivalshare.ArrivalShareViewModel
import com.joon.ringout.presentation.arrivalshare.ArrivalShareRecordSelection
import androidx.navigation3.runtime.EntryProviderScope
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.GetAlarmRecordGroup
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository
import com.joon.ringout.presentation.arrival.ArrivalRoute
import com.joon.ringout.presentation.arrival.ArrivalViewModel
import com.joon.ringout.presentation.arrival.PendingArrivalUiState

internal fun EntryProviderScope<AppRoute>.arrivalGraph(
    navigationState: AppNavigationState,
    repository: ArrivalResultRepository,
    missionHistoryRepository: MissionHistoryRepository? = null,
    pendingArrival: PendingArrivalUiState,
    hasActiveRuntime: Boolean,
    storeProvider: ViewModelStoreProvider? = null,
) {
    entry<AppRoute.ArrivalShare>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val owner = storeProvider?.getOrCreateOwner(AppRoute.Arrival(route.occurrenceId).viewModelStoreKey())
            ?: checkNotNull(LocalViewModelStoreOwner.current)
        val editor = viewModel(viewModelStoreOwner = owner) {
            ArrivalShareViewModel(
                route.occurrenceId, repository, SavedStateHandle(),
                recordLookup = missionHistoryRepository?.let(::GetAlarmRecordGroup),
            )
        }
        ArrivalShareRoute(editor, onBack = { navigationState.popBackStack(route) })
    }
    entry<AppRoute.RecordShare>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val date = runCatching { MissionDate.parse(route.date) }.getOrNull()
        if (date != null) {
            val editor = viewModel {
                ArrivalShareViewModel(
                    occurrenceId = route.occurrenceId ?: "legacy-record:${route.date}:${route.successRecordKey}",
                    repository = repository,
                    savedState = SavedStateHandle(),
                    recordLookup = missionHistoryRepository?.let(::GetAlarmRecordGroup),
                    recordSelection = ArrivalShareRecordSelection(date, route.groupKey, route.successRecordKey),
                )
            }
            ArrivalShareRoute(editor, onBack = { navigationState.popBackStack(route) })
        }
    }
    entry<AppRoute.Arrival>(clazzContentKey = AppRoute::viewModelStoreKey) { route ->
        val arrivalViewModel = viewModel { ArrivalViewModel(route.occurrenceId, repository) }
        ArrivalRoute(
            viewModel = arrivalViewModel,
            onShare = { navigationState.navigate(AppRoute.ArrivalShare(route.occurrenceId)) },
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
