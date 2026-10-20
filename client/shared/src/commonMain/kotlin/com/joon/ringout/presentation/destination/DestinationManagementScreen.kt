package com.joon.ringout.presentation.destination

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.component.DestinationDeleteDialog
import com.joon.ringout.presentation.destination.component.DestinationEditorPanel
import com.joon.ringout.presentation.destination.component.normalizeDestinationNickname

@Composable
internal fun DestinationManagementScreen(
    destination: SavedDestination,
    onBackClick: () -> Unit,
    onSave: (SavedDestination) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false,
    isActive: Boolean = true,
    mapContent: (@Composable (Modifier) -> Unit)? = null,
) {
    var nickname by rememberSaveable(destination.id) { mutableStateOf(destination.name) }
    var showDeleteConfirmation by rememberSaveable(destination.id) { mutableStateOf(false) }
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = isActive,
        onBackCompleted = {
            if (!isSaving) {
                if (showDeleteConfirmation) showDeleteConfirmation = false else onBackClick()
            }
        },
    )
    Box(modifier.fillMaxSize().imePadding()) {
        DestinationMapScreen(
            initialSelection = remember(destination.id) { destination.toDestinationSelection() },
            onBackClick = { if (!isSaving && isActive) onBackClick() },
            mapContent = mapContent,
            onConfirmClick = {},
            onSavedDestinationConfirmClick = {},
            isSaveInProgress = isSaving,
            isDestinationActionEnabled = isActive && !isSaving && !showDeleteConfirmation,
            isBackHandlerEnabled = isActive && !showDeleteConfirmation && !isSaving,
            bottomContent = { selection, canSave ->
                DestinationEditorPanel(
                    nickname = nickname,
                    onNicknameChange = { nickname = it },
                    onDelete = { showDeleteConfirmation = true },
                    onSave = {
                        val name = normalizeDestinationNickname(nickname)
                        if (name != null && canSave) {
                            onSave(destination.copy(name = name, address = selection.address,
                                latitude = selection.latitude, longitude = selection.longitude))
                        }
                    },
                    canSave = canSave && normalizeDestinationNickname(nickname) != null,
                    isSaving = isSaving,
                )
            },
        )
        if (isSaving || !isActive) {
            Box(Modifier.matchParentSize().clickable(interactionSource = null, indication = null) {})
        }
        if (showDeleteConfirmation && isActive) {
            DestinationDeleteDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                onConfirm = onDelete,
                isDeleting = isSaving,
            )
        }
    }
}

@Preview
@Composable
private fun DestinationManagementScreenPreview() {
    RingoutTheme {
        DestinationManagementScreen(SavedDestination(1, "헬스장", "서울특별시 중구 세종대로 110", 37.5665, 126.978), {}, {}, {})
    }
}
