package com.joon.ringout.alarm

import com.joon.ringout.data.alarm.AlarmDataSource
import kotlin.time.Clock
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale

internal data class IosRingingAlarm(
    val systemAlarmId: String,
    val alarmId: String,
    val occurrenceId: String?,
    val retryAttempt: Int,
    val alarmTime: String,
    val destinationName: String,
    val limitMinutes: Int,
    val startedAtEpochMillis: Long,
)

internal suspend fun resolveIosRingingAlarm(
    systemAlarmId: String,
    dataSource: AlarmDataSource,
    deadlineAlarm: IosMissionDeadlineAlarm?,
    startedAtEpochMillis: Long = Clock.System.now().toEpochMilliseconds(),
): IosRingingAlarm? {
    val retryRegistration = deadlineAlarm
        ?.takeIf { registration -> registration.alarmKitId == systemAlarmId }
    val retrySeed = retryRegistration?.missionSeed
    if (retryRegistration != null && retrySeed != null) {
        return IosRingingAlarm(
            systemAlarmId = systemAlarmId,
            alarmId = retrySeed.alarmId,
            occurrenceId = retryRegistration.retryOccurrenceId,
            retryAttempt = retryRegistration.retryAttempt,
            alarmTime = retrySeed.alarmTime,
            destinationName = retrySeed.destinationName,
            limitMinutes = retrySeed.limitMinutes,
            startedAtEpochMillis = startedAtEpochMillis,
        )
    }

    val savedAlarm = dataSource.getById(systemAlarmId) ?: return null
    return IosRingingAlarm(
        systemAlarmId = systemAlarmId,
        alarmId = savedAlarm.request.id,
        occurrenceId = null,
        retryAttempt = 0,
        alarmTime = savedAlarm.request.time,
        destinationName = savedAlarm.request.destinationName,
        limitMinutes = savedAlarm.request.limitMinutes,
        startedAtEpochMillis = startedAtEpochMillis,
    )
}

internal fun ActiveAlarmMission.toIosRingingAlarm(): IosRingingAlarm = IosRingingAlarm(
    systemAlarmId = alarmId,
    alarmId = alarmId,
    occurrenceId = occurrenceId,
    retryAttempt = retryAttempt,
    alarmTime = alarmTime,
    destinationName = destinationName,
    limitMinutes = limitMinutes,
    startedAtEpochMillis = startedAtEpochMillis,
)

internal fun iosAlarmDateText(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        locale = NSLocale(localeIdentifier = "ko_KR")
        dateFormat = "yyyy년 M월 d일 EEEE"
    }
    return formatter.stringFromDate(
        NSDate(
            timeIntervalSinceReferenceDate =
                epochMillis / MillisPerSecond - SecondsFromUnixEpochToAppleReferenceDate,
        ),
    )
}

private const val MillisPerSecond = 1_000.0
private const val SecondsFromUnixEpochToAppleReferenceDate = 978_307_200.0
