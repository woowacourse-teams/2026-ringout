package com.joon.ringout.data.room

import kotlinx.serialization.Serializable

@Serializable
data class RoomCreateRequestEntity(
    val name: String,
    val description: String?,
    val activityDays: List<String>,
    val activityTime: String,
)
