package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.dayOfWeekIndex
import com.joon.ringout.domain.missionhistory.plusDays

data class OngoingRoomActivity(
    val date: MissionDate,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
)

/** Finds a scheduled activity that started today or yesterday and is still inside its one-hour window. */
class CalculateOngoingRoomActivity {
    operator fun invoke(
        schedule: RoomActivitySchedule,
        now: RoomScheduleMoment,
        clock: RoomScheduleClock,
    ): OngoingRoomActivity? {
        if (schedule.days.isEmpty()) return null

        return (0 downTo -1).asSequence()
            .mapNotNull { offset ->
                val date = now.date.plusDays(offset)
                if (schedule.days.none { it.dayOfWeekIndex == date.dayOfWeekIndex }) return@mapNotNull null

                val start = clock.atLocalTime(date, schedule.hour, schedule.minute) ?: return@mapNotNull null
                val end = start + ActivityDurationMillis
                if (start <= now.epochMillis && now.epochMillis < end) {
                    OngoingRoomActivity(date, start, end)
                } else {
                    null
                }
            }
            .maxByOrNull(OngoingRoomActivity::startEpochMillis)
    }
}

private const val ActivityDurationMillis = 60 * 60 * 1_000L
