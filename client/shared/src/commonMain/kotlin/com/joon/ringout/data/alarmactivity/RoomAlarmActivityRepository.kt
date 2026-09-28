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

    override fun observeSummaries(dates: List<MissionDate>): Flow<Map<MissionDate, AlarmActivitySummary>> = flow {
        initializeTracking()
        emitAll(dao.observeDailyCounts(dates.map(MissionDate::iso8601)).map { dailyCounts ->
            val startDate = dailyCounts.firstOrNull()?.trackingStartDate
            val countsByDate = dailyCounts.associateBy(AlarmActivityDailyCounts::localDate)
            dates.associateWith { date ->
                val ringingCount = countsByDate[date.iso8601]?.ringingCount ?: 0
                // A timezone change can put a later event on a local date before tracking began.
                val isTracked = startDate != null && (date.iso8601 >= startDate || ringingCount > 0)
                AlarmActivitySummary(
                    ringingCount = ringingCount.takeIf { isTracked },
                    isTrackingStartDate = isTracked && date.iso8601 <= startDate,
                    observedRingingOnly = observedRingingOnly,
                )
            }
        })
    }
}
