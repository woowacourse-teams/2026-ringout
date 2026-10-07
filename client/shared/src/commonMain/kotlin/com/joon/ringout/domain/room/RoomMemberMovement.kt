package com.joon.ringout.domain.room

data class RoomMemberMovement(
    val userId: Long,
    val nickname: String,
    val status: RoomMemberMovementStatus,
)

enum class RoomMemberMovementStatus {
    Idle,
    AlarmTriggered,
    MovementStarted,
    Moving,
    Arrived,
    GaveUp,
    Unknown,
}
