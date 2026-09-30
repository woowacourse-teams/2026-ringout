package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomScheduleClock
import com.joon.ringout.domain.room.RoomScheduleMoment
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

internal actual fun systemRoomScheduleClock(): RoomScheduleClock = object : RoomScheduleClock {
    override fun now(): RoomScheduleMoment {
        val now = ZonedDateTime.now()
        return RoomScheduleMoment(MissionDate.parse(now.toLocalDate().toString()), now.toInstant().toEpochMilli())
    }

    override fun atLocalTime(date: MissionDate, hour: Int, minute: Int): Long =
        LocalDate.parse(date.iso8601).atTime(hour, minute).atZone(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
}
