package com.joon.ringout.presentation.roommembermanagement

internal data class RoomMemberUiModel(
    val id: String,
    val nickname: String,
    val joinedDate: String?,
    val profileImageUrl: String? = null,
    val isOwner: Boolean = false,
)

internal data class RoomMemberManagementUiState(
    val members: List<RoomMemberUiModel> = emptyList(),
    val canManageMembers: Boolean = false,
    val canRemoveMembers: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val selectedMemberId: String? = null,
) {
    val selectedMember: RoomMemberUiModel?
        get() = members.firstOrNull { it.id == selectedMemberId }
}
