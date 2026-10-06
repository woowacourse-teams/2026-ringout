package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.room.CalculateNextRoomSchedule
import com.joon.ringout.domain.room.NextRoomSchedule
import com.joon.ringout.domain.room.RoomActivityDay
import com.joon.ringout.domain.room.RoomActivitySchedule
import com.joon.ringout.domain.room.RoomScheduleClock
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

internal expect fun systemRoomScheduleClock(): RoomScheduleClock

/** 기존 화면 모델의 표시용 시간을 계산 가능한 도메인 값으로 변환한다. */
internal fun RoomUiModel.toActivitySchedule(): RoomActivitySchedule? {
    val days = activityDays.map { KoreanActivityDays[it.trim()] ?: return null }.toSet()
    val match = ActivityTimePattern.matchEntire(activityTimeText.trim()) ?: return null
    val period = match.groupValues[1]
    val hour = match.groupValues[2].toInt()
    val minute = match.groupValues[3].toInt()
    if (minute !in 0..59) return null
    val hour24 = if (period.isEmpty()) {
        hour.takeIf { it in 0..23 } ?: return null
    } else {
        if (hour !in 1..12) return null
        hour % 12 + if (period == "오후") 12 else 0
    }
    return RoomActivitySchedule(days, hour24, minute)
}

internal fun RoomHomeUiState.withCurrentSchedule(clock: RoomScheduleClock): RoomHomeUiState {
    val schedule = room?.toActivitySchedule()
        ?: return copy(nextScheduleText = null, remainingTimeText = null)
    val now = clock.now()
    val next = CalculateNextRoomSchedule()(schedule, now, clock)
        ?: return copy(nextScheduleText = null, remainingTimeText = null)
    return copy(
        nextScheduleText = next.description(now.date),
        remainingTimeText = formatRoomRemainingTime(next.remainingSeconds),
    )
}

internal fun formatRoomRemainingTime(seconds: Long): String {
    val remaining = seconds.coerceAtLeast(0)
    if (remaining >= 86_400) return "${remaining / 86_400}일 ${remaining % 86_400 / 3_600}시간"
    return listOf(remaining / 3_600, remaining % 3_600 / 60, remaining % 60)
        .joinToString(":") { it.toString().padStart(2, '0') }
}

private fun NextRoomSchedule.description(today: MissionDate): String {
    val dayText = when (date) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> (if (date.year != today.year) "${date.year}년 " else "") + "${date.month}월 ${date.day}일"
    }
    val period = if (hour < 12) "오전" else "오후"
    val hour12 = (if (hour % 12 == 0) 12 else hour % 12).toString().padStart(2, '0')
    return "$dayText $period $hour12:${minute.toString().padStart(2, '0')}"
}

private val ActivityTimePattern = Regex("(?:(오전|오후)\\s+)?(\\d{1,2}):(\\d{2})")
private val KoreanActivityDays = mapOf(
    "월" to RoomActivityDay.Monday, "화" to RoomActivityDay.Tuesday,
    "수" to RoomActivityDay.Wednesday, "목" to RoomActivityDay.Thursday,
    "금" to RoomActivityDay.Friday, "토" to RoomActivityDay.Saturday, "일" to RoomActivityDay.Sunday,
)
