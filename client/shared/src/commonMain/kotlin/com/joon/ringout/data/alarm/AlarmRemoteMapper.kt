package com.joon.ringout.data.alarm

internal fun AlarmWithRepeatDays.toRemoteAlarm(
    alarmId: String,
    alarmVersion: Long,
    repeatTime: Int = alarm.limitMinutes,
): RemoteAlarm = RemoteAlarm(
    alarmId = alarmId,
    time = alarm.time,
    dayOfWeek = if (alarm.repeatEnabled) {
        repeatDays.map { RemoteAlarmDay.entries[it.dayOfWeek - 1] }
    } else {
        emptyList()
    },
    repeatTime = repeatTime,
    destinationName = alarm.destinationLabel,
    destinationLatitude = alarm.destinationLatitude,
    destinationLongitude = alarm.destinationLongitude,
    alarmVersion = alarmVersion,
).normalized()

internal fun RemoteAlarm.toAlarmWithRepeatDays(
    deviceAlarmId: String,
    ownerAccountId: String,
): AlarmWithRepeatDays = AlarmWithRepeatDays(
    alarm = AlarmEntity(
        id = deviceAlarmId,
        time = time,
        repeatEnabled = dayOfWeek.isNotEmpty(),
        limitMinutes = repeatTime.coerceAtLeast(1),
        destinationLabel = destinationName,
        destinationLatitude = destinationLatitude,
        destinationLongitude = destinationLongitude,
        targetDistanceKm = 1.2,
        alarmSoundName = "기본 알람음",
        alarmSoundUri = null,
        enabled = false,
        ownerAccountId = ownerAccountId,
    ),
    repeatDays = dayOfWeek.map { AlarmRepeatDayEntity(deviceAlarmId, it.ordinal + 1) },
)

internal fun RemoteAlarm.normalized(): RemoteAlarm = copy(
    dayOfWeek = dayOfWeek.distinct().sorted(),
    destinationName = destinationName
        .trim()
        .take(20)
        // The server length counts UTF-16 code units. Avoid cutting a surrogate pair in half.
        .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
        .trimEnd(),
)
