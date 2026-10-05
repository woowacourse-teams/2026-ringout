package com.joon.ringout.domain.room

data class RoomManagementMember(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String?,
    val joinedAt: String,
    val membershipRole: RoomMembershipRole,
)
