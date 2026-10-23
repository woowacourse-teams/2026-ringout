package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var lastManagedDestination by remember(viewModel) { mutableStateOf(state.managedDestination) }
    if (isActive && state.managedDestination != null) lastManagedDestination = state.managedDestination
    val canEdit = isActive && state.managedDestination != null
    LaunchedEffect(state.managedDestination, isActive) {
        if (isActive && state.managedDestination == null) onFinished()
    }
    // 관리 상태를 닫은 뒤에도 퇴장 중인 항목이 빈 화면으로 바뀌지 않게 한다.
    lastManagedDestination?.let { destination ->
        DestinationManagementScreen(
            destination = destination,
            // The parent closes management and pops together, so a completed swipe can settle immediately.
            onBackClick = { if (canEdit) onFinished() },
            onSave = { if (canEdit) viewModel.saveManagedDestination(it) },
            onDelete = { if (canEdit) viewModel.deleteManagedDestination() },
            modifier = modifier,
            isSaving = state.isSaving,
            isActive = canEdit,
        )
    }
    if (isActive) {
        AppMessageHost(
            state = state.errorMessage?.let { AppMessageHostState("목적지를 변경하지 못했어요", it) },
            onDismiss = viewModel::clearError,
        )
    }
}
