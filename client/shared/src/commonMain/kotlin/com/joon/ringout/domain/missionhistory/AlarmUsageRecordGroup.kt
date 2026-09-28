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

/** Newest groups first; unknown legacy alarm IDs stay separate instead of guessing a relation. */
fun List<AlarmUsageRecord>.groupByAlarm(): List<AlarmUsageRecordGroup> =
    sortedWith(compareBy({ it.date.iso8601 }, { it.ringingStartedAtEpochMillis ?: Long.MIN_VALUE }))
        .asReversed()
        .groupBy { record ->
            val identity = record.alarmId?.takeIf(String::isNotBlank)?.let { "alarm:$it" }
                ?: "record:${record.key}"
            "${record.date.iso8601}:$identity"
        }
        .map { (key, newestFirst) ->
            AlarmUsageRecordGroup(
                key = key,
                date = newestFirst.first().date,
                alarmId = newestFirst.first().alarmId,
                entries = newestFirst.asReversed(),
            )
        }
