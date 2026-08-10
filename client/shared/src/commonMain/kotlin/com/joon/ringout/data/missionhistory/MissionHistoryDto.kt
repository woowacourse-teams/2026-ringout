package com.joon.ringout.data.missionhistory

data class MissionHistoryDto(
    val result: String,
    val completedAt: String,
    val occurrenceId: String? = null,
)
