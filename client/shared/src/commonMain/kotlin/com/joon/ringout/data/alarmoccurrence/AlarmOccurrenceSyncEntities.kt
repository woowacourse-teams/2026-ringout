package com.joon.ringout.data.alarmoccurrence

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** 알람 설정을 삭제해도 보존한다. ownerAccountId는 재실행 후에도 같은 계정을 식별하는 값이다. */
@Entity(
    tableName = "alarm_occurrence_sync",
    indices = [Index(value = ["owner_account_id"]), Index(value = ["server_occurrence_id"], unique = true)],
)
data class AlarmOccurrenceSyncEntity(
    @PrimaryKey @ColumnInfo(name = "local_execution_id") val localExecutionId: String,
    @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
    @ColumnInfo(name = "alarm_id") val alarmId: String,
    @ColumnInfo(name = "schedule_version") val scheduleVersion: Long,
    @ColumnInfo(name = "scheduled_at") val scheduledAtEpochMillis: Long?,
    // 로컬에서는 미확인 시각을 보관할 수 있지만 API의 startedAt 필수 조건은 유지한다.
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long?,
    @ColumnInfo(name = "server_occurrence_id") val serverOccurrenceId: String? = null,
)

@Entity(
    tableName = "alarm_occurrence_ringing_links",
    foreignKeys = [ForeignKey(
        entity = AlarmOccurrenceSyncEntity::class,
        parentColumns = ["local_execution_id"], childColumns = ["local_execution_id"],
    )],
    indices = [Index(value = ["local_execution_id"]), Index(value = ["event_id"], unique = true)],
)
data class AlarmOccurrenceRingingLinkEntity(
    @PrimaryKey @ColumnInfo(name = "local_ringing_id") val localRingingId: String,
    @ColumnInfo(name = "local_execution_id") val localExecutionId: String,
    /** 최초 울림은 null, 재울림은 서버에 보낼 별도 ID를 한 번 저장해 재사용한다. */
    @ColumnInfo(name = "event_id") val eventId: String?,
    @ColumnInfo(name = "ringing_at") val ringingAtEpochMillis: Long?,
)

enum class AlarmOccurrenceOutboxKind {
    START, INITIAL_DISMISSED, REPEAT_RANG, REPEAT_DISMISSED, ARRIVED, FORCE_ENDED,
}

enum class AlarmOccurrenceOutboxState { PENDING, BLOCKED, SENT }

@Entity(
    tableName = "alarm_occurrence_outbox",
    foreignKeys = [
        ForeignKey(entity = AlarmOccurrenceSyncEntity::class,
            parentColumns = ["local_execution_id"], childColumns = ["local_execution_id"]),
        ForeignKey(entity = AlarmOccurrenceRingingLinkEntity::class,
            parentColumns = ["local_ringing_id"], childColumns = ["local_ringing_id"]),
    ],
    indices = [
        Index(value = ["local_execution_id", "deduplication_key"], unique = true),
        Index(value = ["local_ringing_id"]),
        Index(value = ["state", "next_attempt_at"]),
    ],
)
data class AlarmOccurrenceOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_execution_id") val localExecutionId: String,
    @ColumnInfo(name = "local_ringing_id") val localRingingId: String,
    @ColumnInfo(name = "deduplication_key") val deduplicationKey: String,
    val kind: AlarmOccurrenceOutboxKind,
    @ColumnInfo(name = "occurred_at") val occurredAtEpochMillis: Long?,
    val state: AlarmOccurrenceOutboxState = AlarmOccurrenceOutboxState.PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAtEpochMillis: Long? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "last_error_message") val lastErrorMessage: String? = null,
)
