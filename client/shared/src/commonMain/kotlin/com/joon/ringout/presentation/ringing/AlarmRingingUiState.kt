package com.joon.ringout.presentation.ringing

data class AlarmRingingUiState(
    val id: String,
    val alarmTime: String,
    val dateText: String,
    val limitMinutes: Int,
    val destinationName: String,
)
