package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
internal data class RoomMemberMovementsResponseEntity(
    val members: List<RoomMemberMovementEntity>,
)

@Serializable
internal data class RoomMemberMovementEntity(
    val userId: Long,
    val nickname: String,
    val status: String,
    val profileImageUrl: String? = null,
)
