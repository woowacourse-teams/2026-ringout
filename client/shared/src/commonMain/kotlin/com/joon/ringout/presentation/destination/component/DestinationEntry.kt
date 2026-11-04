package com.joon.ringout.presentation.destination.component

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.DestinationUiState

/** 목적지 목록 확인 전에는 지도를 생성하지 않고, 호출한 화면 위에서 선택을 받는다. */
@Composable
internal fun DestinationEntry(
    uiState: DestinationUiState,
    isActive: Boolean,
    onFindInMap: () -> Unit,
    onSelect: (SavedDestination) -> Unit,
    onRetry: () -> Unit,
    onDeleteRequest: (Long) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteDismiss: () -> Unit,
    openRequestId: Long = 0,
    content: @Composable (onDestinationClick: () -> Unit) -> Unit,
) {
    var isRequested by rememberSaveable { mutableStateOf(false) }
    var keepEmptyListOpenAfterDelete by rememberSaveable { mutableStateOf(false) }
    var consumedOpenRequest by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(isActive, openRequestId) {
        if (isActive && openRequestId > consumedOpenRequest) {
            consumedOpenRequest = openRequestId
            isRequested = uiState.destinations.isNotEmpty()
        }
    }
    val canOpenMap = !uiState.isLoading && uiState.errorMessage == null && uiState.destinations.isEmpty()

    LaunchedEffect(isActive, isRequested, canOpenMap) {
        if (!isActive && isRequested) {
            isRequested = false
        } else if (isRequested && canOpenMap && !keepEmptyListOpenAfterDelete) {
            isRequested = false
            onFindInMap()
        }
    }
    content {
        if (isActive) {
            if (canOpenMap) onFindInMap() else isRequested = true
        }
    }
    if (isRequested && isActive) {
        DestinationSelectionDialog(
            destinations = uiState.destinations,
            onDismissRequest = {
                isRequested = false
                keepEmptyListOpenAfterDelete = false
                if (uiState.pendingDeleteDestinationId != null) onDeleteDismiss()
            },
            onSelect = {
                isRequested = false
                onSelect(it)
            },
            onFindInMap = {
                isRequested = false
                onFindInMap()
            },
            isLoading = uiState.isLoading,
            errorMessage = uiState.errorMessage,
            onRetry = onRetry,
            isDeleteConfirmationOpen = uiState.pendingDeleteDestinationId != null,
            onDelete = {
                keepEmptyListOpenAfterDelete = true
                onDeleteRequest(it.id)
            },
        )
    }
    if (isRequested && isActive && uiState.pendingDeleteDestinationId != null) {
        DestinationDeleteDialog(
            onDismissRequest = onDeleteDismiss,
            onConfirm = onDeleteConfirm,
            isDeleting = uiState.isDeletingDestination,
            errorMessage = uiState.deleteErrorMessage,
        )
    }
}

@Preview
@Composable
private fun DestinationEntryPreview() {
    RingoutTheme {
        DestinationEntry(DestinationUiState(isLoading = false), true, {}, {}, {}, {}, {}, {}) { onClick ->
            TextButton(onClick = onClick) { Text("목적지 선택") }
        }
    }
}
