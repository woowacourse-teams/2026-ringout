package com.joon.ringout.domain.missionhistory

/** The local record identity and timestamps needed to share one successful alarm group. */
data class AlarmRecordShareData(
    val groupKey: String,
    val date: MissionDate,
    val successRecordKey: String,
    val occurrenceId: String?,
    val wakeAtEpochMillis: Long,
    val arrivedAtEpochMillis: Long,
)

/**
 * A group is shareable only when its first ringing and selected success have known times.
 * Without a selected record key, use the latest success in the group.
 * Entries are ordered by [groupByAlarm]; do not substitute a later ringing or an older success
 * when either selected time is missing.
 */
fun AlarmUsageRecordGroup.toShareableAlarmRecord(successRecordKey: String? = null): AlarmRecordShareData? {
    val first = entries.first()
    val wakeAt = first.ringingScheduledAtEpochMillis ?: first.ringingStartedAtEpochMillis ?: return null
    val success = entries.lastOrNull {
        it.result == MissionResult.SUCCESS && (successRecordKey == null || it.key == successRecordKey)
    } ?: return null
    val arrivedAt = success.missionCompletedAtEpochMillis ?: return null

    return AlarmRecordShareData(
        groupKey = key,
        date = date,
        successRecordKey = success.key,
        occurrenceId = success.occurrenceId,
        wakeAtEpochMillis = wakeAt,
        arrivedAtEpochMillis = arrivedAt,
    )
}
