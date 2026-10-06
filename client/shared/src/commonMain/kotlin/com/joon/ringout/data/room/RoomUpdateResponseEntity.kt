package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomUpdateResponseEntity(
    val roomId: Long,
    val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
)
