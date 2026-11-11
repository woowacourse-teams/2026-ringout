package com.joon.ringout.data.missionhistory

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.withReadTransaction
import com.joon.ringout.data.alarmoccurrence.CachedAlarmOccurrenceRow
import com.joon.ringout.data.alarmoccurrence.captureAlarmOccurrenceAccount
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.domain.alarmactivity.AlarmActivityRepository
import com.joon.ringout.domain.alarmactivity.AlarmActivitySummary
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.AlarmUsageRecord
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.RecordsHistoryRepository
import com.joon.ringout.domain.missionhistory.RecordsHistorySnapshot
import com.joon.ringout.domain.missionhistory.recordSortTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class AlarmUsageRecordWithCacheLink(
    @Embedded val record: AlarmUsageRecordRow,
    @ColumnInfo(name = "server_occurrence_id") val serverOccurrenceId: String?,
    @ColumnInfo(name = "event_id") val eventId: String?,
)

class RoomRecordsHistoryRepository(
    private val database: RingoutDatabase,
    private val tokenStorage: SecureTokenStorage,
    private val authSession: AuthSession,
    private val alarmActivityRepository: AlarmActivityRepository,
) : RecordsHistoryRepository {
    private val missionHistoryDao = database.missionHistoryDao()
    private val cacheDao = database.alarmOccurrenceCacheDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observe(dates: List<MissionDate>): Flow<RecordsHistorySnapshot> =
        combine(authSession.state, authSession.identity) { _, _ ->
            captureAlarmOccurrenceAccount(tokenStorage, authSession)
        }.flatMapLatest { account ->
            if (dates.isEmpty()) return@flatMapLatest flowOf(RecordsHistorySnapshot(emptyList()))
            val visibleDates = dates.toSet()
            val start = dates.minOf(MissionDate::iso8601)
            val end = dates.maxOf(MissionDate::iso8601)
            val summaries = alarmActivityRepository.observeSummaries(dates)
            if (account == null) {
                combine(missionHistoryDao.observeRecords(start, end), summaries) { rows, localSummaries ->
                    val records = rows.map { it.toDomain() }.filter { it.date in visibleDates }
                    RecordsHistorySnapshot(records, localSummaries)
                }
            } else {
                val owner = account.ownerAccountId
                val snapshot = database.invalidationTracker.createFlow(
                    "alarm_activity_events",
                    "alarm_occurrence_times",
                    "mission_history",
                    "alarm_occurrence_ringing_links",
                    "alarm_occurrence_sync",
                    "alarm_occurrence_outbox",
                    "alarm_occurrence_cache",
                    "alarm_occurrence_cache_ringings",
                    "alarm_occurrence_cache_months",
                ).map {
                    val (local, cached, completeMonths) = database.withReadTransaction {
                        Triple(
                            missionHistoryDao.getRecordsWithCacheLinks(owner, start, end),
                            cacheDao.getOccurrences(owner, start, end),
                            cacheDao.getCompleteMonths(owner),
                        )
                    }
                    mergeRecords(local, cached, visibleDates) to completeMonths.toSet()
                }
                combine(snapshot, summaries) { (records, completed), localSummaries ->
                    val counts = records.groupingBy(AlarmUsageRecord::date).fold(0) { count, row ->
                        count + row.ringingCount
                    }
                    RecordsHistorySnapshot(records, dates.associateWith { date ->
                        val localSummary = localSummaries[date] ?: AlarmActivitySummary()
                        val ringingCount = counts[date] ?: 0
                        val monthCompleted = date.iso8601.substring(0, 7) in completed
                        localSummary.copy(
                            ringingCount = ringingCount.takeIf { it > 0 || monthCompleted },
                            isTrackingStartDate = !monthCompleted && localSummary.isTrackingStartDate,
                            observedRingingOnly = !monthCompleted && localSummary.observedRingingOnly,
                        )
                    })
                }
            }
        }.buffer(0)
}

internal fun mergeRecords(
    local: List<AlarmUsageRecordWithCacheLink>,
    cached: List<CachedAlarmOccurrenceRow>,
    visibleDates: Set<MissionDate>,
): List<AlarmUsageRecord> {
    val localByExecution = local.filter { it.serverOccurrenceId != null }.groupBy { it.serverOccurrenceId }
    val result = local.filter { it.serverOccurrenceId == null }.map { it.record.toDomain() }.toMutableList()
    cached.forEach { row ->
        val date = MissionDate.parse(row.occurrence.date)
        if (date in visibleDates) {
            result += mergeExecution(row, date, localByExecution[row.occurrence.occurrenceId].orEmpty())
        }
    }
    return result.filter { it.date in visibleDates }
        .sortedWith(compareBy({ it.date.iso8601 }, { it.recordSortTime }))
}

private fun mergeExecution(
    row: CachedAlarmOccurrenceRow,
    date: MissionDate,
    local: List<AlarmUsageRecordWithCacheLink>,
): List<AlarmUsageRecord> {
    val occurrence = row.occurrence
    val localByRinging = local.associateBy { it.eventId?.let { id -> "REPEAT:$id" } ?: "INITIAL" }
    val rings = row.ringings.map { ringing ->
        val localRecord = localByRinging[ringing.ringingKey]?.record
        AlarmUsageRecord(
            key = "server:${occurrence.occurrenceId}:${ringing.ringingKey}",
            date = date,
            occurrenceId = localRecord?.occurrenceId,
            ringingStartedAtEpochMillis = localRecord?.ringingStartedAtEpochMillis ?: ringing.ringingAtEpochMillis,
            ringingStoppedAtEpochMillis = localRecord?.ringingStoppedAtEpochMillis ?: ringing.dismissedAtEpochMillis,
            isRingingStartObserved = true,
            alarmId = occurrence.alarmId,
            serverOccurrenceId = occurrence.occurrenceId,
            alarmTime = occurrence.alarmTime,
            ringingCount = 1,
        )
    }.toMutableList()
    val serverRingingKeys = row.ringings.mapTo(mutableSetOf()) { it.ringingKey }
    localByRinging.forEach { (key, localRinging) ->
        val localRecord = localRinging.record.toDomain()
        if (key !in serverRingingKeys && localRecord.ringingCount > 0) {
            rings += localRecord.copy(
                date = date,
                alarmId = occurrence.alarmId,
                serverOccurrenceId = occurrence.occurrenceId,
                alarmTime = occurrence.alarmTime,
                ringingScheduledAtEpochMillis = null,
                result = null,
                missionCompletedAtEpochMillis = null,
                ringingCount = 1,
            )
        }
    }
    rings.sortBy { it.ringingStartedAtEpochMillis ?: it.ringingStoppedAtEpochMillis ?: Long.MIN_VALUE }
    val localTerminal = local.map { it.record.toDomain() }
        .filter { it.result != null }
        .maxByOrNull { it.missionCompletedAtEpochMillis ?: Long.MIN_VALUE }
    val serverResult = when (occurrence.endType) {
        "ARRIVED" -> MissionResult.SUCCESS
        "FORCE_ENDED" -> MissionResult.FAILURE
        else -> null
    }
    val terminalResult = localTerminal?.result ?: serverResult
    if (terminalResult != null && rings.isNotEmpty()) {
        rings[rings.lastIndex] = rings.last().copy(
            result = terminalResult,
            missionCompletedAtEpochMillis = localTerminal?.missionCompletedAtEpochMillis ?: occurrence.endedAtEpochMillis,
        )
    }
    return rings
}
