package com.joon.ringout.domain.alarm

class CalculateNextAlarm {
    data class Alarm(
        val id: String,
        val hour: Int,
        val minute: Int,
        val repeatDays: Set<Day> = emptySet(),
        val isEnabled: Boolean,
    ) {
        init {
            require(hour in 0..23) { "알람의 시는 0 이상 23 이하여야 합니다." }
            require(minute in 0..59) { "알람의 분은 0 이상 59 이하여야 합니다." }
        }
    }
    
    data class CurrentTime(
        val day: Day,
        val hour: Int,
        val minute: Int,
    ) {
        init {
            require(hour in 0..23) { "현재 시각의 시는 0 이상 23 이하여야 합니다." }
            require(minute in 0..59) { "현재 시각의 분은 0 이상 59 이하여야 합니다." }
        }
    }

    data class Result(
        val alarmId: String,
        val minutesUntil: Int,
    )

    enum class Day {
        MONDAY,
        TUESDAY,
        WEDNESDAY,
        THURSDAY,
        FRIDAY,
        SATURDAY,
        SUNDAY,
        ;

        companion object {
            fun fromIndex(index: Int): Day? = entries.getOrNull(index)
        }
    }

    operator fun invoke(
        alarms: List<Alarm>,
        currentTime: CurrentTime,
    ): Result? = alarms
        .asSequence()
        .filter(Alarm::isEnabled)
        .map { alarm ->
            Result(
                alarmId = alarm.id,
                minutesUntil = alarm.minutesUntil(currentTime),
            )
        }
        .minByOrNull(Result::minutesUntil)

    private fun Alarm.minutesUntil(currentTime: CurrentTime): Int {
        val alarmMinuteOfDay = hour * MinutesPerHour + minute
        val currentMinuteOfDay = currentTime.hour * MinutesPerHour + currentTime.minute

        if (repeatDays.isEmpty()) {
            val todayDelay = alarmMinuteOfDay - currentMinuteOfDay
            return if (todayDelay > 0) todayDelay else todayDelay + MinutesPerDay
        }

        for (dayOffset in 0..DaysPerWeek) {
            val candidateDay = Day.entries[
                (currentTime.day.ordinal + dayOffset) % DaysPerWeek
            ]
            if (candidateDay !in repeatDays) continue

            val delay = dayOffset * MinutesPerDay + alarmMinuteOfDay - currentMinuteOfDay
            if (delay > 0) return delay
        }

        error("반복 알람은 일주일 안에 다음 알람 시각이 있어야 합니다.")
    }

    private companion object {
        const val MinutesPerHour = 60
        const val MinutesPerDay = 24 * MinutesPerHour
        const val DaysPerWeek = 7
    }
}
