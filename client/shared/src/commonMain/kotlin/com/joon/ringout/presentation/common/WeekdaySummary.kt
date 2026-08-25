package com.joon.ringout.presentation.common

internal val WeekdayOrder = listOf("월", "화", "수", "목", "금", "토", "일")

internal fun weekdaySummary(days: List<String>): String =
    when (days.toSet()) {
        WeekdayOrder.toSet() -> "매일"
        setOf("토", "일") -> "주말"
        setOf("월", "화", "수", "목", "금") -> "평일"
        else -> WeekdayOrder.filter { it in days }.joinToString(" ")
    }
