package com.joon.ringout.presentation.roommembermanagement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomMemberManagementViewModelTest {
    @Test
    fun `추방을 선택한 뒤 취소하면 회원 목록을 유지한다`() {
        val viewModel = ownerViewModel()

        viewModel.onRemoveMemberClick(firstMember.id)
        assertEquals(firstMember, viewModel.uiState.value.selectedMember)
        assertEquals(members, viewModel.uiState.value.members)

        viewModel.onDismissRemove()
        viewModel.onConfirmRemove()

        assertNull(viewModel.uiState.value.selectedMember)
        assertEquals(members, viewModel.uiState.value.members)
    }

    @Test
    fun `같은 닉네임의 회원 중 선택한 식별자만 임시 목록에서 제외한다`() {
        val viewModel = ownerViewModel()

        viewModel.onRemoveMemberClick(firstMember.id)
        viewModel.onConfirmRemove()
        viewModel.onConfirmRemove()
        viewModel.onRemoveMemberClick(firstMember.id)
        viewModel.onConfirmRemove()

        assertEquals(listOf(owner, secondMember), viewModel.uiState.value.members)
        assertNull(viewModel.uiState.value.selectedMember)
    }

    @Test
    fun `방장이 아닌 사용자는 회원을 선택하거나 추방할 수 없다`() {
        val viewModel = RoomMemberManagementViewModel(initialMembers = members)

        viewModel.onRemoveMemberClick(firstMember.id)
        viewModel.onConfirmRemove()

        assertEquals(members, viewModel.uiState.value.members)
        assertNull(viewModel.uiState.value.selectedMember)
    }

    @Test
    fun `방장 본인과 존재하지 않는 회원은 추방 대상으로 선택하지 않는다`() {
        val viewModel = ownerViewModel()

        viewModel.onRemoveMemberClick(owner.id)
        viewModel.onConfirmRemove()
        viewModel.onRemoveMemberClick("unknown-member")
        viewModel.onConfirmRemove()

        assertEquals(members, viewModel.uiState.value.members)
        assertNull(viewModel.uiState.value.selectedMember)
    }

    @Test
    fun `화면의 임시 추방은 새 화면의 초기 회원 목록에 영향을 주지 않는다`() {
        val viewModel = ownerViewModel()
        viewModel.onRemoveMemberClick(firstMember.id)
        viewModel.onConfirmRemove()

        assertEquals(members, ownerViewModel().uiState.value.members)
    }

    private fun ownerViewModel() = RoomMemberManagementViewModel(
        initialMembers = members,
        canManageMembers = true,
    )

    private companion object {
        val owner = RoomMemberUiModel("owner", "방장", "2026-09-15", isOwner = true)
        val firstMember = RoomMemberUiModel("member-1", "아침러너", "2026-09-16")
        val secondMember = RoomMemberUiModel("member-2", "아침러너", "2026-09-17")
        val members = listOf(owner, firstMember, secondMember)
    }
}
