package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.domain.room.RoomSummary

internal fun RoomEntity.toDomain(): RoomSummary = RoomSummary(
    id = roomId,
    name = name,
    description = description,
    imageUrl = imageUrl.toRoomImageUrl(),
    activityDays = activityDays,
    activityTime = activityTime,
    memberCount = memberCount,
    isJoined = isJoined,
    createdAt = createdAt,
)

private fun String?.toRoomImageUrl(): String? {
    val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (value == DefaultRoomImagePath) return null
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
        return value
    }
    return ApiConfig.url(value)
}

private const val DefaultRoomImagePath = "/images/default-room.png"
