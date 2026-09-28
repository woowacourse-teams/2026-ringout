package com.joon.ringout.presentation.social.model

data class SocialUiState(
    val allRooms: List<RoomUiModel> = emptyList(),
    val joinedRooms: List<RoomUiModel> = emptyList(),
    val isLoadingAllRooms: Boolean = false,
    val allRoomsErrorMessage: String? = null,
    val isAuthenticated: Boolean = false,
)
