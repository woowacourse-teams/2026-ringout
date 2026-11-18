package com.joon.ringout.presentation.arrival

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joon.ringout.presentation.arrivalshare.ArrivalShareRoute
import com.joon.ringout.presentation.arrivalshare.ArrivalShareViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.GetAlarmRecordGroup
import com.joon.ringout.domain.missionhistory.MissionHistoryRepository

/** Only permits acknowledging a local arrival while normal app entry remains restricted. */
@Composable
fun RestrictedArrivalHost(
    repository: ArrivalResultRepository,
    missionHistoryRepository: MissionHistoryRepository? = null,
    hasActiveRuntime: Boolean,
    isEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!isEnabled) {
        content()
        return
    }
    // Share the parent-scoped observer with App when an update gate replaces its composition.
    val pendingViewModel = viewModel { PendingArrivalViewModel(repository) }
    val pending by pendingViewModel.uiState.collectAsStateWithLifecycle()
    val result = pending.result
    if (pending.isLoaded && result != null) {
        key(result.occurrenceId) {
            val owner = remember {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            DisposableEffect(owner) {
                onDispose { owner.viewModelStore.clear() }
            }
            val arrivalViewModel = viewModel(viewModelStoreOwner = owner) {
                ArrivalViewModel(result.occurrenceId, repository)
            }
            var editing by remember { mutableStateOf(false) }
            val editor = viewModel(viewModelStoreOwner = owner) {
                ArrivalShareViewModel(result.occurrenceId, repository, recordLookup = missionHistoryRepository?.let(::GetAlarmRecordGroup))
            }
            if (hasActiveRuntime) content()
            else if (editing) ArrivalShareRoute(editor, onBack = { editing = false })
            else ArrivalRoute(
                viewModel = arrivalViewModel,
                onShare = { editing = true },
                // Acknowledgement invalidates the receipt query and reveals the original gate.
                onConfirmed = {},
            )
        }
    } else {
        content()
    }
}
