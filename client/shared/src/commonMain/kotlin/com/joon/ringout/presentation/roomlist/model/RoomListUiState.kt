package com.joon.ringout.presentation.roomlist.model

import com.joon.ringout.presentation.roomlist.model.RoomUiModel

data class RoomListUiState(
    val allRooms: List<RoomUiModel> = emptyList(),
    val joinedRooms: List<RoomUiModel> = emptyList(),
    val isLoadingAllRooms: Boolean = false,
    val allRoomsErrorMessage: String? = null,
    val isRefreshingAllRooms: Boolean = false,
    val allRoomsRefreshErrorMessage: String? = null,
    val isAuthenticated: Boolean = false,
)
