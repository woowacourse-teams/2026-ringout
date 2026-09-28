package com.joon.ringout.domain.alarmactivity

import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AlarmActivitySummary(
    val ringingCount: Int? = null,
    val isTrackingStartDate: Boolean = false,
    val observedRingingOnly: Boolean = false,
)

interface AlarmActivityRepository {
    /** Emits a summary for every requested date, including dates without ringing events. */
    fun observeSummaries(dates: List<MissionDate>): Flow<Map<MissionDate, AlarmActivitySummary>>

    fun observeSummary(date: MissionDate): Flow<AlarmActivitySummary> =
        observeSummaries(listOf(date)).map { it.getValue(date) }
}
