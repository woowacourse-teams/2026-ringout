package com.joon.ringout.data.alarmmovement

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxState
import com.joon.ringout.domain.alarmmovement.AlarmMovementAction

/** roomId가 null인 행은 가입 모임 조회 대기, 값이 있는 행은 모임별 POST 대기다. */
@Entity(
    tableName = "alarm_movement_outbox",
    foreignKeys = [ForeignKey(entity = AlarmOccurrenceSyncEntity::class,
        parentColumns = ["local_execution_id"], childColumns = ["local_execution_id"])],
    indices = [Index(value = ["local_execution_id", "deduplication_key"], unique = true)],
)
data class AlarmMovementOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "local_execution_id") val localExecutionId: String,
    @ColumnInfo(name = "deduplication_key") val deduplicationKey: String,
    val action: AlarmMovementAction,
    @ColumnInfo(name = "room_id") val roomId: Long? = null,
    @ColumnInfo(name = "occurred_at") val occurredAtEpochMillis: Long,
    val state: AlarmOccurrenceOutboxState = AlarmOccurrenceOutboxState.PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAtEpochMillis: Long? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
)
