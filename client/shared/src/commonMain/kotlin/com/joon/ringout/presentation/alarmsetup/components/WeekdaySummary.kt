package com.joon.ringout.presentation.alarmsetup.components

internal val alarmSetupWeekdayOrder = listOf("월", "화", "수", "목", "금", "토", "일")

internal fun weekdaySummary(days: List<String>): String =
    when (days.toSet()) {
        alarmSetupWeekdayOrder.toSet() -> "매일"
        setOf("토", "일") -> "주말"
        setOf("월", "화", "수", "목", "금") -> "평일"
        else -> alarmSetupWeekdayOrder.filter { it in days }.joinToString(" ")
    }
