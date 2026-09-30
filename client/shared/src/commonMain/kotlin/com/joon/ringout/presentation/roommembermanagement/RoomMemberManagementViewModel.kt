package com.joon.ringout.presentation.roommembermanagement

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** UI 확인용 상태만 관리한다. 회원 조회·추방 API 및 영구 저장은 연결하지 않는다. */
internal class RoomMemberManagementViewModel(
    initialMembers: List<RoomMemberUiModel> = emptyList(),
    canManageMembers: Boolean = false,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(
        RoomMemberManagementUiState(
            members = initialMembers.toList(),
            canManageMembers = canManageMembers,
        ),
    )
    val uiState = mutableUiState.asStateFlow()

    fun onRemoveMemberClick(memberId: String) {
        mutableUiState.update { state ->
            val member = state.members.firstOrNull { it.id == memberId }
            if (!state.canManageMembers || member == null || member.isOwner) state
            else state.copy(selectedMemberId = member.id)
        }
    }

    fun onDismissRemove() {
        mutableUiState.update { it.copy(selectedMemberId = null) }
    }

    /** 확인한 회원을 현재 화면의 임시 목록에서만 제외한다. */
    fun onConfirmRemove() {
        mutableUiState.update { state ->
            val member = state.selectedMember
            if (!state.canManageMembers || member == null || member.isOwner) {
                state.copy(selectedMemberId = null)
            } else {
                state.copy(
                    members = state.members.filterNot { it.id == member.id },
                    selectedMemberId = null,
                )
            }
        }
    }
}
