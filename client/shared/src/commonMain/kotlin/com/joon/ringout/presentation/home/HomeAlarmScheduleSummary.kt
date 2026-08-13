package com.joon.ringout.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.joon.ringout.presentation.LocalClockSnapshot
import com.joon.ringout.presentation.currentLocalClockSnapshot
import kotlinx.coroutines.delay

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
    val minutesUntilNext = alarms
        .asSequence()
        .filter(HomeAlarm::isEnabled)
        .mapNotNull { alarm -> alarm.minutesUntilNext(clock) }
        .minOrNull()
        ?: return "켜진 알람이 없습니다."

    val hours = minutesUntilNext / MinutesPerHour
    val minutes = minutesUntilNext % MinutesPerHour
    val duration = buildList {
        if (hours > 0) add("${hours}시간")
        if (minutes > 0) add("${minutes}분")
    }.joinToString(" ")

    return "$duration 후 알람이 울려요."
}

private fun HomeAlarm.minutesUntilNext(clock: LocalClockSnapshot): Int? {
    if (!AlarmTimePattern.matches(time)) return null

    val hour = time.substringBefore(":").toInt()
    val minute = time.substringAfter(":").toInt()
    val alarmMinuteOfDay = hour * MinutesPerHour + minute
    val currentMinuteOfDay = clock.hour * MinutesPerHour + clock.minute
    val repeatDayIndexes = if (repeatEnabled) {
        selectedDays.mapNotNull(DayIndexByKorean::get).toSet()
    } else {
        emptySet()
    }

    if (repeatDayIndexes.isEmpty()) {
        val todayDelay = alarmMinuteOfDay - currentMinuteOfDay
        return if (todayDelay > 0) todayDelay else todayDelay + MinutesPerDay
    }

    for (dayOffset in 0..DaysPerWeek) {
        val candidateDay = (clock.dayOfWeek + dayOffset) % DaysPerWeek
        if (candidateDay !in repeatDayIndexes) continue

        val delay = dayOffset * MinutesPerDay + alarmMinuteOfDay - currentMinuteOfDay
        if (delay > 0) return delay
    }

    return null
}

private const val MinuteInMilliseconds = 60_000L
private const val MinutesPerHour = 60
private const val MinutesPerDay = 24 * MinutesPerHour
private const val DaysPerWeek = 7

private val AlarmTimePattern = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")

private val DayIndexByKorean = mapOf(
    "월" to 0,
    "화" to 1,
    "수" to 2,
    "목" to 3,
    "금" to 4,
    "토" to 5,
    "일" to 6,
)
