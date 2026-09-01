package com.joon.ringout.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.joon.ringout.domain.alarm.CalculateNextAlarm
import com.joon.ringout.domain.alarm.CalculateNextAlarm.Alarm as AlarmForCalculation
import com.joon.ringout.domain.alarm.CalculateNextAlarm.CurrentTime
import com.joon.ringout.domain.alarm.CalculateNextAlarm.Day
import com.joon.ringout.presentation.LocalClockSnapshot
import com.joon.ringout.presentation.currentLocalClockSnapshot
import com.joon.ringout.presentation.home.model.HomeAlarm
import kotlinx.coroutines.delay

private val calculateNextAlarm = CalculateNextAlarm()

@Composable
internal fun rememberNextAlarmDescription(alarms: List<HomeAlarm>): String {
    var clock by remember { mutableStateOf(currentLocalClockSnapshot()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(MinuteInMilliseconds)
            clock = currentLocalClockSnapshot()
        }
    }

    return remember(alarms, clock) {
        nextAlarmDescription(
            alarms = alarms,
            clock = clock,
        )
    }
}

internal fun nextAlarmDescription(
    alarms: List<HomeAlarm>,
    clock: LocalClockSnapshot,
): String {
    val currentTime = clock.toAlarmCurrentTime()
        ?: return "켜진 알람이 없습니다."
    val minutesUntilNext = calculateNextAlarm(
        alarms = alarms.mapNotNull(HomeAlarm::toAlarmForCalculation),
        currentTime = currentTime,
    )?.minutesUntil ?: return "켜진 알람이 없습니다."

    val hours = minutesUntilNext / MinutesPerHour
    val minutes = minutesUntilNext % MinutesPerHour
    val duration = buildList {
        if (hours > 0) add("${hours}시간")
        if (minutes > 0) add("${minutes}분")
    }.joinToString(" ")

    return "$duration 후 알람이 울려요."
}

private fun HomeAlarm.toAlarmForCalculation(): AlarmForCalculation? {
    if (!AlarmTimePattern.matches(time)) return null

    return AlarmForCalculation(
        id = id,
        hour = time.substringBefore(":").toInt(),
        minute = time.substringAfter(":").toInt(),
        repeatDays = if (repeatEnabled) {
            selectedDays.mapNotNull(DayByKorean::get).toSet()
        } else {
            emptySet()
        },
        isEnabled = isEnabled,
    )
}

private fun LocalClockSnapshot.toAlarmCurrentTime(): CurrentTime? {
    val day = Day.fromIndex(dayOfWeek) ?: return null
    return CurrentTime(
        day = day,
        hour = hour,
        minute = minute,
    )
}

private const val MinuteInMilliseconds = 60_000L
private const val MinutesPerHour = 60

private val AlarmTimePattern = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")

private val DayByKorean = mapOf(
    "월" to Day.MONDAY,
    "화" to Day.TUESDAY,
    "수" to Day.WEDNESDAY,
    "목" to Day.THURSDAY,
    "금" to Day.FRIDAY,
    "토" to Day.SATURDAY,
    "일" to Day.SUNDAY,
)
