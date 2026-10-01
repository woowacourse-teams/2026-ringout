package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomMembershipResponseEntity(
    val roomId: Long,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val activityDays: List<String>,
    val activityTime: String,
    val memberCount: Int,
    val membershipRole: String,
    val createdAt: String,
    val members: List<RoomMemberEntity>,
)

@Serializable
data class RoomMemberEntity(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String? = null,
)
