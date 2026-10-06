package com.joon.ringout.domain.room

data class RoomSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val activityDays: List<String>,
    val activityTime: String,
    val memberCount: Int,
    val isJoined: Boolean,
    val createdAt: String,
)
