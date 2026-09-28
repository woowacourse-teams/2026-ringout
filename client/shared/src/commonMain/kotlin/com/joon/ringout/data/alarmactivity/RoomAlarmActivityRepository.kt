package com.joon.ringout.data.alarmactivity

import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class RoomAlarmActivityRepository(
    private val dao: AlarmActivityDao,
    private val observedRingingOnly: Boolean = false,
) : AlarmActivityRepository {
    suspend fun initializeTracking() {
        val now = currentAlarmActivityTimestamp()
        dao.initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = now.epochMillis, localDate = now.localDate))
    }

    override fun observeSummary(date: MissionDate): Flow<AlarmActivitySummary> = flow {
        initializeTracking()
        emitAll(dao.observeCounts(date.iso8601).map { counts ->
            val startDate = counts.trackingStartDate
            // A timezone change can put a later event on a local date before tracking began.
            val hasEvents = counts.ringingCount > 0
            val isTracked = startDate != null && (date.iso8601 >= startDate || hasEvents)
            AlarmActivitySummary(
                ringingCount = counts.ringingCount.takeIf { isTracked },
                isTrackingStartDate = isTracked && date.iso8601 <= startDate,
                observedRingingOnly = observedRingingOnly,
            )
        })
    }
}
