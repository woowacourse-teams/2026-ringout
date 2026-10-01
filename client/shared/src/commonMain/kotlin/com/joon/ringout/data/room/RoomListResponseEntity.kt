package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomListResponseEntity(
    val rooms: List<RoomEntity>,
)
