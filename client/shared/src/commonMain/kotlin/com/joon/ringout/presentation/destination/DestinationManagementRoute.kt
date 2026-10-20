package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.joon.ringout.presentation.common.component.AppMessageHost
import com.joon.ringout.presentation.common.component.AppMessageHostState

@Composable
internal fun DestinationManagementRoute(
    viewModel: DestinationViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
) {
    val state = viewModel.uiState
    LaunchedEffect(state.managedDestination, isActive) {
        if (isActive && state.managedDestination == null) onFinished()
    }
    state.managedDestination?.let { destination ->
        DestinationManagementScreen(
            destination = destination,
            onBackClick = viewModel::closeManagement,
            onSave = viewModel::saveManagedDestination,
            onDelete = viewModel::deleteManagedDestination,
            modifier = modifier,
            isSaving = state.isSaving,
            isActive = isActive,
        )
    }
    if (isActive) {
        AppMessageHost(
            state = state.errorMessage?.let { AppMessageHostState("목적지를 변경하지 못했어요", it) },
            onDismiss = viewModel::clearError,
        )
    }
}
