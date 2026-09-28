package com.joon.ringout.domain.alarmactivity

import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.flow.Flow

data class AlarmActivitySummary(
    val ringingCount: Int? = null,
    val isTrackingStartDate: Boolean = false,
    val observedRingingOnly: Boolean = false,
)

interface AlarmActivityRepository {
    fun observeSummary(date: MissionDate): Flow<AlarmActivitySummary>
}
