package com.joon.ringout.domain.room

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays

internal class FakeRoomScheduleClock(
    private val anchor: MissionDate = MissionDate.parse("2026-09-28"),
    var elapsedMillis: Long = 0,
    private val targetOffsetMillis: Long = 0,
) : RoomScheduleClock {
    var reads: Int = 0
        private set

    override fun now(): RoomScheduleMoment {
        reads++
        return RoomScheduleMoment(anchor.plusDays((elapsedMillis / 86_400_000).toInt()), elapsedMillis)
    }

    override fun atLocalTime(date: MissionDate, hour: Int, minute: Int): Long {
        val days = (-7..21).first { anchor.plusDays(it) == date }
        return days * 86_400_000L + hour * 3_600_000L + minute * 60_000L + targetOffsetMillis
    }
}
