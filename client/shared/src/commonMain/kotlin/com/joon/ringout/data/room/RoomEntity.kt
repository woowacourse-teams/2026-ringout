package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomEntity(
    val roomId: Long,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val activityDays: List<String>,
    val activityTime: String,
    val memberCount: Int,
    val isJoined: Boolean,
    val createdAt: String,
)
