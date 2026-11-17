package com.joon.ringout.data.destination

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

enum class DestinationOutboxOperation { UPSERT, DELETE }

enum class DestinationOutboxState { PENDING, BLOCKED }

@Entity(
    tableName = "destination_outbox",
    indices = [
        Index(value = ["owner_account_id", "remote_destination_id", "id"]),
        Index(value = ["owner_account_id", "state", "next_attempt_at"]),
    ],
)
data class DestinationOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
    @ColumnInfo(name = "local_destination_id") val localDestinationId: Long,
    @ColumnInfo(name = "remote_destination_id") val remoteDestinationId: String,
    val operation: DestinationOutboxOperation,
    @ColumnInfo(name = "payload_alias") val payloadAlias: String? = null,
    @ColumnInfo(name = "payload_latitude") val payloadLatitude: Double? = null,
    @ColumnInfo(name = "payload_longitude") val payloadLongitude: Double? = null,
    @ColumnInfo(name = "destination_version") val destinationVersion: Long? = null,
    val state: DestinationOutboxState = DestinationOutboxState.PENDING,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAtEpochMillis: Long? = null,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String? = null,
    @ColumnInfo(name = "last_error_message") val lastErrorMessage: String? = null,
)

@Entity(tableName = "destination_sync_state")
data class DestinationSyncStateEntity(
    @PrimaryKey @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
    @ColumnInfo(name = "restore_complete") val restoreComplete: Boolean = false,
    @ColumnInfo(name = "restore_attempt_count") val restoreAttemptCount: Int = 0,
    @ColumnInfo(name = "restore_next_attempt_at") val restoreNextAttemptAtEpochMillis: Long? = null,
    @ColumnInfo(name = "last_restore_error_code") val lastRestoreErrorCode: String? = null,
    @ColumnInfo(name = "last_restore_error_message") val lastRestoreErrorMessage: String? = null,
)
