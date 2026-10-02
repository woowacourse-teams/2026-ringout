package com.joon.ringout.presentation.roommembermanagement

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.roommembermanagement.component.RoomMemberManagementPreviewMembers

/** Preview의 실행 모드에서만 샘플 회원의 선택·취소·추방을 확인한다. */
@Preview(name = "회원 관리 · 동작 확인", widthDp = 402, heightDp = 941)
@Composable
private fun RoomMemberManagementInteractivePreview() {
    var removedMemberIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedMemberId by rememberSaveable { mutableStateOf<String?>(null) }

    RingoutTheme(themeMode = ThemeMode.Light) {
        RoomMemberManagementScreen(
            uiState = RoomMemberManagementUiState(
                members = RoomMemberManagementPreviewMembers.filterNot {
                    it.id in removedMemberIds
                },
                canManageMembers = true,
                canRemoveMembers = true,
                selectedMemberId = selectedMemberId,
            ),
            onBackClick = {},
            onRemoveMemberClick = { selectedMemberId = it },
            onDismissRemove = { selectedMemberId = null },
            onConfirmRemove = {
                selectedMemberId?.let { removedMemberIds = removedMemberIds + it }
                selectedMemberId = null
            },
        )
    }
}
