package com.joon.ringout.data.alarm

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Transaction
    @Query("SELECT * FROM alarms ORDER BY time ASC, id ASC")
    fun observeAll(): Flow<List<AlarmWithRepeatDays>>

    @Transaction
    @Query("SELECT * FROM alarms ORDER BY time ASC, id ASC")
    suspend fun getAll(): List<AlarmWithRepeatDays>

    @Transaction
    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY time ASC, id ASC")
    suspend fun getEnabled(): List<AlarmWithRepeatDays>

    @Transaction
    @Query("SELECT * FROM alarms WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): AlarmWithRepeatDays?

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM storage_migrations
            WHERE id = :id
        )
        """,
    )
    suspend fun hasStorageMigration(id: String): Boolean

    @Upsert
    suspend fun upsertAlarm(alarm: AlarmEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRepeatDays(repeatDays: List<AlarmRepeatDayEntity>)

    @Query("DELETE FROM alarm_repeat_days WHERE alarm_id = :alarmId")
    suspend fun deleteRepeatDays(alarmId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStorageMigration(migration: StorageMigrationEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean): Int

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: String): Int

    @Transaction
    suspend fun replace(alarm: AlarmWithRepeatDays) {
        upsertAlarm(alarm.alarm)
        deleteRepeatDays(alarm.alarm.id)
        if (alarm.repeatDays.isNotEmpty()) {
            insertRepeatDays(alarm.repeatDays)
        }
    }

    @Transaction
    suspend fun importLegacyIfNeeded(
        alarms: List<AlarmWithRepeatDays>,
        migration: StorageMigrationEntity,
    ): Boolean {
        if (hasStorageMigration(migration.id)) return false

        alarms.forEach { alarm ->
            upsertAlarm(alarm.alarm)
            deleteRepeatDays(alarm.alarm.id)
            if (alarm.repeatDays.isNotEmpty()) {
                insertRepeatDays(alarm.repeatDays)
            }
        }
        insertStorageMigration(migration)
        return true
    }
}
