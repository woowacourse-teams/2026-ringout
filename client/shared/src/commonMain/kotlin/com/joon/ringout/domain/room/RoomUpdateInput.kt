package com.joon.ringout.domain.room

data class RoomUpdateInput(
    val name: String? = null,
    val description: String? = null,
    val image: RoomImageUpload? = null,
    // 이미지 삭제 기능이 도입되기 전까지는 false로 고정한다.
    val removeImage: Boolean = false,
    val activityDays: List<String>? = null,
    val activityTime: String? = null,
) {
    val hasChanges: Boolean = name != null || description != null || image != null || activityDays != null || activityTime != null
}
