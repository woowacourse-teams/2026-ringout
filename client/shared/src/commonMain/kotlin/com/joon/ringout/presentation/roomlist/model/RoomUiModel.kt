package com.joon.ringout.presentation.roomlist.model

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.presentation.common.weekdaySummary

data class RoomUiModel(
    val id: String,
    val representativeImage: String? = null,
    val name: String,
    val description: String,
    val createdAt: String,
    val activityDays: List<String>,
    val activityTimeText: String,
    val participantCount: Int,
    val isJoined: Boolean,
) {
    init {
        require(description.isNotBlank()) { "Room description must not be blank." }
        require(createdAt.isNotBlank()) { "Room creation date must not be blank." }
        parseCreatedAtDate(createdAt)
    }

    val activityDaysText: String
        get() = weekdaySummary(activityDays)

    val createdAtText: String
        get() = parseCreatedAtDate(createdAt).let { date ->
            "${date.year}년 ${date.month}월 ${date.day}일 개설"
        }
}

/** The room API returns a timezone-free LocalDateTime; only its calendar date is displayed. */
private fun parseCreatedAtDate(value: String): MissionDate {
    require(RoomCreatedAtPattern.matches(value)) {
        "Room creation date must use yyyy-MM-dd or yyyy-MM-dd'T'HH:mm:ss."
    }
    val date = MissionDate.parse(value.substring(0, 10))
    if (value.length == 10) return date

    val hour = value.substring(11, 13).toInt()
    val minute = value.substring(14, 16).toInt()
    val second = value.substring(17, 19).toInt()
    require(hour in 0..23 && minute in 0..59 && second in 0..59) {
        "Room creation time does not exist."
    }
    return date
}

private val RoomCreatedAtPattern = Regex(
    """\d{4}-\d{2}-\d{2}(?:T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?)?""",
)
