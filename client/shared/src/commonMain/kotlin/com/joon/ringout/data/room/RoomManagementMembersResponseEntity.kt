package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomManagementMembersResponseEntity(
    val members: List<RoomManagementMemberEntity>,
)

@Serializable
data class RoomManagementMemberEntity(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String? = null,
    val joinedAt: String,
    val membershipRole: String,
)
