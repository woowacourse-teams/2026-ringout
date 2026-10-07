package com.joon.ringout.presentation.roomlist.model

import com.joon.ringout.domain.room.RoomSummary

internal fun RoomSummary.toRoomUiModel(): RoomUiModel = RoomUiModel(
    id = id.toString(),
    representativeImage = imageUrl,
    name = name,
    description = description.orEmpty(),
    createdAt = createdAt,
    activityDays = activityDays.map { day ->
        RoomWeekdayLabels[day] ?: error("알 수 없는 모임 활동 요일입니다: $day")
    }.sortedBy { RoomWeekdayOrder.indexOf(it) },
    activityTimeText = activityTime.toKoreanTimeText(),
    participantCount = memberCount,
    isJoined = isJoined,
)

internal fun String.toKoreanTimeText(): String {
    require(RoomActivityTimePattern.matches(this)) { "모임 활동 시간은 HH:mm 형식이어야 해요." }
    val hour = substringBefore(':').toInt()
    val minute = substringAfter(':').toInt()
    require(hour in 0..23 && minute in 0..59) { "존재하지 않는 모임 활동 시간이에요." }

    val period = if (hour < 12) "오전" else "오후"
    val displayHour = when (val twelveHour = hour % 12) {
        0 -> 12
        else -> twelveHour
    }
    return "$period $displayHour:${minute.toString().padStart(2, '0')}"
}

internal val RoomWeekdayLabels = mapOf(
    "MONDAY" to "월",
    "TUESDAY" to "화",
    "WEDNESDAY" to "수",
    "THURSDAY" to "목",
    "FRIDAY" to "금",
    "SATURDAY" to "토",
    "SUNDAY" to "일",
)

private val RoomWeekdayOrder = listOf("월", "화", "수", "목", "금", "토", "일")

private val RoomActivityTimePattern = Regex("\\d{2}:\\d{2}")

internal fun RoomUiModel.withUpdatedSchedule(result: com.joon.ringout.domain.room.RoomUpdateResult): RoomUiModel = copy(
    activityDays = result.activityDays?.map { day -> checkNotNull(RoomWeekdayLabels[day]) }
        ?.sortedBy { RoomWeekdayOrder.indexOf(it) } ?: activityDays,
    activityTimeText = result.activityTime?.toKoreanTimeText() ?: activityTimeText,
)
