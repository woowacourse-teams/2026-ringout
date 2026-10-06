package com.joon.ringout.domain.room

data class RoomUpdateResult(
    val roomId: Long,
    val name: String,
    val description: String?,
    val imageUrl: String?,
)
