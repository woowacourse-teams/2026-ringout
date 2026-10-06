package com.joon.ringout.alarm

import com.joon.ringout.data.alarmactivity.AlarmActivityDao
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

/** Resolve the configured clock time on or before the observed start/stop, including midnight. */
internal fun iosInitialRingingScheduledAt(alarmTime: String, referenceEpochMillis: Long): Long? {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "en_US_POSIX")
        dateFormat = "yyyy-MM-dd HH:mm"
        lenient = false
    }
    val candidate = formatter.dateFromString("${iosMissionDate(referenceEpochMillis)} $alarmTime") ?: return null
    val candidateMillis = ((candidate.timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000).toLong()
    if (candidateMillis <= referenceEpochMillis) return candidateMillis
    val previousDay = NSCalendar.currentCalendar.dateByAddingUnit(NSCalendarUnitDay, -1, candidate, 0u) ?: return null
    return ((previousDay.timeIntervalSinceReferenceDate + 978_307_200.0) * 1_000).toLong()
}

internal suspend fun AlarmActivityDao.recordInitialRingingSchedule(
    occurrenceId: String,
    alarmTime: String,
    referenceEpochMillis: Long,
) {
    if (getOccurrenceTimes(occurrenceId)?.ringingScheduledAtEpochMillis != null) return
    val scheduledAt = iosInitialRingingScheduledAt(alarmTime, referenceEpochMillis) ?: return
    mergeOccurrenceTimes(AlarmOccurrenceTimesEntity(occurrenceId, ringingScheduledAtEpochMillis = scheduledAt))
}
