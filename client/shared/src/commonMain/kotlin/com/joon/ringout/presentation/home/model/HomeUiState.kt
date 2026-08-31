package com.joon.ringout.presentation.home.model

data class HomeUiState(
    val isLoading: Boolean = true,
    val alarms: List<HomeAlarm> = emptyList(),
    val errorMessage: String? = null,
)
