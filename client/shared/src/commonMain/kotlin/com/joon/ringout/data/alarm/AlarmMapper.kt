package com.joon.ringout.data.alarm

import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule

internal fun AlarmScheduleRequest.validateForStorage(): AlarmScheduleRequest = apply {
    require(id.isNotBlank()) { "Alarm id must not be blank." }
    require(AlarmTimePattern.matches(time)) {
        "Alarm time must use 24-hour HH:mm format: $time"
    }
    require(limitMinutes > 0) { "Alarm limitMinutes must be positive: $limitMinutes" }
    require(destinationName.isNotBlank()) { "Alarm destinationName must not be blank." }
    require(destinationLatitude.isFinite() && destinationLatitude in ValidLatitudeRange) {
        "Alarm destinationLatitude is out of range: $destinationLatitude"
    }
    require(destinationLongitude.isFinite() && destinationLongitude in ValidLongitudeRange) {
        "Alarm destinationLongitude is out of range: $destinationLongitude"
    }
    require(targetDistanceKm.isFinite() && targetDistanceKm > 0.0) {
        "Alarm targetDistanceKm must be finite and positive: $targetDistanceKm"
    }
    require(alarmSoundName.isNotBlank()) { "Alarm alarmSoundName must not be blank." }
    selectedDays.forEach { day ->
        require(day in IsoDayOfWeekByKoreanDay) { "Unsupported alarm repeat day: $day" }
    }
}

internal fun SavedAlarmSchedule.validateForStorage(): SavedAlarmSchedule = apply {
    request.validateForStorage()
}

internal fun AlarmScheduleRequest.toAlarmWithRepeatDays(
    enabled: Boolean,
): AlarmWithRepeatDays {
    validateForStorage()
    val repeatDays = selectedDays
        .map { day -> IsoDayOfWeekByKoreanDay.getValue(day) }
        .distinct()
        .sorted()
        .map { dayOfWeek ->
            AlarmRepeatDayEntity(
                alarmId = id,
                dayOfWeek = dayOfWeek,
            )
        }

    return AlarmWithRepeatDays(
        alarm = AlarmEntity(
            id = id,
            time = time,
            repeatEnabled = repeatEnabled,
            limitMinutes = limitMinutes,
            destinationName = destinationName,
            destinationAddress = destinationAddress,
            destinationLatitude = destinationLatitude,
            destinationLongitude = destinationLongitude,
            targetDistanceKm = targetDistanceKm,
            alarmSoundName = alarmSoundName,
            alarmSoundUri = alarmSoundUri,
            enabled = enabled,
        ),
        repeatDays = repeatDays,
    )
}

internal fun SavedAlarmSchedule.toAlarmWithRepeatDays(): AlarmWithRepeatDays =
    request.toAlarmWithRepeatDays(enabled = enabled)

internal fun AlarmWithRepeatDays.toSavedAlarmSchedule(): SavedAlarmSchedule {
    repeatDays.forEach { repeatDay ->
        require(repeatDay.alarmId == alarm.id) {
            "Repeat day belongs to ${repeatDay.alarmId}, expected ${alarm.id}."
        }
    }
    val selectedDays = repeatDays
        .map(AlarmRepeatDayEntity::dayOfWeek)
        .distinct()
        .sorted()
        .map { dayOfWeek ->
            requireNotNull(KoreanDayByIsoDayOfWeek[dayOfWeek]) {
                "Unsupported persisted alarm repeat day: $dayOfWeek"
            }
        }
    val request = AlarmScheduleRequest(
        id = alarm.id,
        time = alarm.time,
        selectedDays = selectedDays,
        repeatEnabled = alarm.repeatEnabled,
        limitMinutes = alarm.limitMinutes,
        destinationName = alarm.destinationName,
        destinationAddress = alarm.destinationAddress,
        destinationLatitude = alarm.destinationLatitude,
        destinationLongitude = alarm.destinationLongitude,
        targetDistanceKm = alarm.targetDistanceKm,
        alarmSoundName = alarm.alarmSoundName,
        alarmSoundUri = alarm.alarmSoundUri,
    ).validateForStorage()

    return SavedAlarmSchedule(
        request = request,
        enabled = alarm.enabled,
    )
}

private val AlarmTimePattern = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
private val ValidLatitudeRange = -90.0..90.0
private val ValidLongitudeRange = -180.0..180.0

private val IsoDayOfWeekByKoreanDay = mapOf(
    "월" to 1,
    "화" to 2,
    "수" to 3,
    "목" to 4,
    "금" to 5,
    "토" to 6,
    "일" to 7,
)

private val KoreanDayByIsoDayOfWeek = IsoDayOfWeekByKoreanDay
    .entries
    .associate { (day, dayOfWeek) -> dayOfWeek to day }
