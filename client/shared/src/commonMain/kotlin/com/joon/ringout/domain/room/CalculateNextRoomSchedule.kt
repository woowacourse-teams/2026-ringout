package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.dayOfWeekIndex
import com.joon.ringout.domain.missionhistory.plusDays

enum class RoomActivityDay(val dayOfWeekIndex: Int) {
    Monday(1), Tuesday(2), Wednesday(3), Thursday(4), Friday(5), Saturday(6), Sunday(0),
}

data class RoomActivitySchedule(val days: Set<RoomActivityDay>, val hour: Int, val minute: Int) {
    init {
        require(hour in 0..23 && minute in 0..59)
    }
}

data class RoomScheduleMoment(val date: MissionDate, val epochMillis: Long)

interface RoomScheduleClock {
    fun now(): RoomScheduleMoment

    /** 기기 시간대의 현지 시각을 실제 시각으로 변환한다. 표현할 수 없는 시각은 null이다. */
    fun atLocalTime(date: MissionDate, hour: Int, minute: Int): Long?
}

data class NextRoomSchedule(
    val date: MissionDate,
    val hour: Int,
    val minute: Int,
    val remainingSeconds: Long,
)

/** 이미 시작한 일정은 제외한다. 초 경계 전에는 0초가 표시되지 않도록 올림한다. */
class CalculateNextRoomSchedule {
    operator fun invoke(
        schedule: RoomActivitySchedule,
        now: RoomScheduleMoment,
        clock: RoomScheduleClock,
    ): NextRoomSchedule? {
        if (schedule.days.isEmpty()) return null
        for (offset in 0..7) {
            val date = now.date.plusDays(offset)
            if (schedule.days.none { it.dayOfWeekIndex == date.dayOfWeekIndex }) continue
            val target = clock.atLocalTime(date, schedule.hour, schedule.minute) ?: continue
            val remainingMillis = target - now.epochMillis
            if (remainingMillis <= 0) continue
            return NextRoomSchedule(date, schedule.hour, schedule.minute, (remainingMillis + 999) / 1_000)
        }
        return null
    }
}
