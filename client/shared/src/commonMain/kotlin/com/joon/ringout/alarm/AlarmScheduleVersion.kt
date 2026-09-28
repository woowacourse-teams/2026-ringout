package com.joon.ringout.alarm

/** Resolve before scheduling so the OS reservation and saved settings carry the same version. */
internal fun AlarmScheduleRequest.withScheduleVersion(previous: AlarmScheduleRequest?): AlarmScheduleRequest {
    val changed = previous != null && (
        time != previous.time || selectedDays.toSet() != previous.selectedDays.toSet() ||
            repeatEnabled != previous.repeatEnabled
        )
    return copy(scheduleVersion = when {
        previous == null -> 1
        changed -> previous.scheduleVersion + 1
        else -> previous.scheduleVersion
    })
}
