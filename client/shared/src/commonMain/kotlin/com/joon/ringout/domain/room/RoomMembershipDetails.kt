package com.joon.ringout.domain.room

data class RoomMembershipDetails(
    val room: RoomSummary,
    val membershipRole: RoomMembershipRole,
    val members: List<RoomMemberDetails>,
)

enum class RoomMembershipRole {
    OWNER,
    MEMBER,
}

data class RoomMemberDetails(
    val userId: Long,
    val nickname: String,
    val profileImageUrl: String? = null,
    val membershipRole: RoomMembershipRole? = null,
)
