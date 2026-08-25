package com.joon.ringout.presentation.home.model

data class HomeAlarm(
    val id: String,
    val time: String,
    val days: String,
    val destination: String,
    val timeLimitMinutes: Int,
    val isEnabled: Boolean,
    val targetAddress: String = "",
    val targetLatitude: Double? = null,
    val targetLongitude: Double? = null,
    val targetDistanceKm: Double = 1.2,
    val alarmSoundName: String = "기본 알람음",
    val alarmSoundUri: String? = null,
    val selectedDays: List<String> = emptyList(),
    val repeatEnabled: Boolean = true,
)
