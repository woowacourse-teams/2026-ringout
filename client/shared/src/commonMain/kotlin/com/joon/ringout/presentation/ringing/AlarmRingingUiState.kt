package com.joon.ringout.presentation.ringing

data class AlarmRingingUiState(
    val id: String,
    val limitMinutes: Int,
    val destinationName: String,
)
