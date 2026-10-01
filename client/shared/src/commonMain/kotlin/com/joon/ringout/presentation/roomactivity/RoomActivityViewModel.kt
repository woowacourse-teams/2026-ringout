package com.joon.ringout.presentation.roomactivity

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 화면 전환만 관리한다. 서버·알람·로컬 DB에 접근하거나 활동 기록을 생성하지 않는다. */
internal class RoomActivityViewModel(initialState: RoomActivityUiState) : ViewModel() {
    private val mutableUiState = MutableStateFlow(initialState)
    val uiState = mutableUiState.asStateFlow()

    fun onMembersClick(memberIds: List<String>) {
        mutableUiState.update { state ->
            state.copy(selectedMemberIds = memberIds.distinct().filter { id -> state.members.any { it.id == id } })
        }
    }

    fun onCloseMembers() {
        mutableUiState.update { it.copy(selectedMemberIds = null) }
    }
}
