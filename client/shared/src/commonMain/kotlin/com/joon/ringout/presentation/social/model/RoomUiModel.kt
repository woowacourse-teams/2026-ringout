package com.joon.ringout.presentation.social.model

import com.joon.ringout.presentation.common.weekdaySummary

data class RoomUiModel(
    val id: String,
    val representativeImage: String? = null,
    val name: String,
    val activityDays: List<String>,
    val activityTimeText: String,
    val participantCount: Int,
    val isJoined: Boolean,
) {
    val activityDaysText: String
        get() = weekdaySummary(activityDays)
}
