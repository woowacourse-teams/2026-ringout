package com.joon.ringout.domain.missionhistory

/** One alarm's occurrences on one local day, ordered from the first ringing to the last. */
data class AlarmUsageRecordGroup(
    val key: String,
    val date: MissionDate,
    val alarmId: String?,
    val entries: List<AlarmUsageRecord>,
) {
    init {
        require(entries.isNotEmpty()) { "An alarm record group must contain an occurrence." }
    }
}

/** Use the stop or completion time when the start is unknown; keep unknown legacy alarm IDs separate. */
fun List<AlarmUsageRecord>.groupByAlarm(): List<AlarmUsageRecordGroup> =
    sortedWith(compareBy({ it.date.iso8601 }, {
        it.ringingScheduledAtEpochMillis ?: it.ringingStartedAtEpochMillis ?: it.ringingStoppedAtEpochMillis
            ?: it.missionCompletedAtEpochMillis ?: Long.MIN_VALUE
    }))
        .groupBy { record ->
            val identity = record.alarmId?.takeIf(String::isNotBlank)?.let { "alarm:$it" }
                ?: "record:${record.key}"
            "${record.date.iso8601}:$identity"
        }
        .map { (key, chronologicalEntries) ->
            AlarmUsageRecordGroup(
                key = key,
                date = chronologicalEntries.first().date,
                alarmId = chronologicalEntries.first().alarmId,
                entries = chronologicalEntries,
            )
        }
