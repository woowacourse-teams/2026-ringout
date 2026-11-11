package com.joon.ringout.domain.alarmoccurrence

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.hours

fun interface AlarmOccurrenceMonthLoader {
    /** Returns false when this month could not be loaded and may be retried. */
    suspend fun ensure(month: MissionYearMonth): Boolean
}

/** The server groups occurrences by Asia/Seoul (UTC+09:00). */
internal fun alarmOccurrenceServerDate(instant: Instant): MissionDate =
    MissionDate.parse((instant + 9.hours).toString().substring(0, 10))

fun currentAlarmOccurrenceMonth(now: Instant = Clock.System.now()): MissionYearMonth {
    val date = alarmOccurrenceServerDate(now)
    return MissionYearMonth(date.year, date.month)
}
