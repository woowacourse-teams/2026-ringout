package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomKickRequestEntity(
    val userId: Long,
)
