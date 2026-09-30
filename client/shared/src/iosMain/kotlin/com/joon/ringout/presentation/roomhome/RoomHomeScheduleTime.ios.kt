package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomScheduleClock
import com.joon.ringout.domain.room.RoomScheduleMoment
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone

internal actual fun systemRoomScheduleClock(): RoomScheduleClock = object : RoomScheduleClock {
    override fun now(): RoomScheduleMoment {
        val now = NSDate()
        val formatter = NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            calendar = calendar()
            timeZone = NSTimeZone.localTimeZone
            dateFormat = "yyyy-MM-dd"
        }
        return RoomScheduleMoment(
            MissionDate.parse(formatter.stringFromDate(now)),
            ((now.timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000).toLong(),
        )
    }

    override fun atLocalTime(date: MissionDate, hour: Int, minute: Int): Long? {
        val components = NSDateComponents().apply {
            year = date.year.toLong()
            month = date.month.toLong()
            day = date.day.toLong()
            this.hour = hour.toLong()
            this.minute = minute.toLong()
            second = 0
        }
        return calendar().dateFromComponents(components)?.let {
            ((it.timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000).toLong()
        }
    }

    private fun calendar(): NSCalendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply {
        timeZone = NSTimeZone.localTimeZone
    }
}
