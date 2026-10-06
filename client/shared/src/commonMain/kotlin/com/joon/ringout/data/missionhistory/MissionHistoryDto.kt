package com.joon.ringout.data.missionhistory

data class MissionHistoryDto(
    val result: String,
    val completedAt: String,
    val occurrenceId: String? = null,
    val ringingStartedAtEpochMillis: Long? = null,
    val ringingStoppedAtEpochMillis: Long? = null,
    val missionCompletedAtEpochMillis: Long? = null,
    val isRingingStartObserved: Boolean = false,
)
