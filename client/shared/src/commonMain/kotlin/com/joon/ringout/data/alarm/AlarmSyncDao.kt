package com.joon.ringout.data.alarm

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.Insert
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "alarm_outbox",
    indices = [Index(value = ["owner_account_id", "remote_alarm_id", "id"])],
)
data class AlarmOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
    @ColumnInfo(name = "remote_alarm_id") val remoteAlarmId: String,
    val payload: String?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int = 0,
    @ColumnInfo(name = "next_attempt_at") val nextAttemptAt: Long? = null,
    val state: String = "PENDING",
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)

@Entity(tableName = "alarm_restore")
data class AlarmRestoreEntity(
    @PrimaryKey @ColumnInfo(name = "owner_account_id") val ownerAccountId: String,
)

@Dao
interface AlarmSyncDao {
    @Query("SELECT * FROM alarm_outbox ORDER BY id")
    fun observeOutbox(): Flow<List<AlarmOutboxEntity>>

    @Query("SELECT * FROM alarm_outbox WHERE owner_account_id = :owner ORDER BY id")
    suspend fun getOutbox(owner: String): List<AlarmOutboxEntity>

    @Insert
    suspend fun insertOutbox(entity: AlarmOutboxEntity)

    @Update
    suspend fun updateOutbox(entity: AlarmOutboxEntity)

    @Query("DELETE FROM alarm_outbox WHERE id = :id")
    suspend fun deleteOutbox(id: Long)

    @Query("DELETE FROM alarm_outbox WHERE owner_account_id = :owner AND remote_alarm_id = :alarmId AND state = 'REJECTED'")
    suspend fun deleteRejected(owner: String, alarmId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM alarm_restore WHERE owner_account_id = :owner)")
    suspend fun getRestoreComplete(owner: String): Boolean

    @Upsert
    suspend fun putRestoreComplete(entity: AlarmRestoreEntity)

    suspend fun setRestoreComplete(owner: String) = putRestoreComplete(AlarmRestoreEntity(owner))

    @Query("DELETE FROM alarm_restore WHERE owner_account_id = :owner")
    suspend fun clearRestoreComplete(owner: String)
}
