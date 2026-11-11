package com.joon.ringout.data.alarmoccurrence

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index

@Entity(tableName = "alarm_occurrence_cache_months", primaryKeys = ["owner_account_id", "month"])
data class CachedAlarmOccurrenceMonthEntity(
    @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
    val month: String,
)

@Entity(
    tableName = "alarm_occurrence_cache",
    primaryKeys = ["owner_account_id", "occurrence_id"],
    indices = [Index(value = ["owner_account_id", "date"])],
)
data class CachedAlarmOccurrenceEntity(
    @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
    val month: String,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "alarm_id") val alarmId: String,
    val date: String,
    @ColumnInfo(name = "alarm_time") val alarmTime: String,
    @ColumnInfo(name = "end_type") val endType: String?,
    @ColumnInfo(name = "ended_at") val endedAtEpochMillis: Long?,
)

@Entity(
    tableName = "alarm_occurrence_cache_ringings",
    primaryKeys = ["owner_account_id", "occurrence_id", "ringing_key"],
    foreignKeys = [
        ForeignKey(
            entity = CachedAlarmOccurrenceEntity::class,
            parentColumns = ["owner_account_id", "occurrence_id"],
            childColumns = ["owner_account_id", "occurrence_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class CachedAlarmRingingEntity(
    @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
    @ColumnInfo(name = "occurrence_id") val occurrenceId: String,
    @ColumnInfo(name = "ringing_key") val ringingKey: String,
    @ColumnInfo(name = "event_id") val eventId: String?,
    @ColumnInfo(name = "ringing_at") val ringingAtEpochMillis: Long,
    @ColumnInfo(name = "dismissed_at") val dismissedAtEpochMillis: Long?,
)
