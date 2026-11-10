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
    suspend fun insertAlarm(alarm: AlarmEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRepeatDays(repeatDays: List<AlarmRepeatDayEntity>)

    @Query("DELETE FROM alarm_repeat_days WHERE alarm_id = :alarmId")
    suspend fun deleteRepeatDays(alarmId: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStorageMigration(migration: StorageMigrationEntity)

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean): Int

    @Query("UPDATE alarms SET enabled = 0")
    suspend fun disableAll(): Int

    /** One-time legacy guest migration; never changes an existing owner or a conflicting account's alarm. */
    @Query("""
        SELECT * FROM alarms
        WHERE owner_account_id IS NULL
            AND NOT EXISTS (
                SELECT 1 FROM alarm_occurrence_sync e
                WHERE e.alarm_id = alarms.id AND e.owner_account_id != :ownerAccountId
            )
            AND NOT EXISTS (
                SELECT 1 FROM ios_alarm_occurrence_recording d
                WHERE d.alarm_id = alarms.id AND d.owner_account_id IS NOT NULL
                    AND d.owner_account_id != :ownerAccountId
            )
    """)
    suspend fun getEligibleUnownedLegacyAlarms(ownerAccountId: String): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE owner_account_id = :ownerAccountId")
    suspend fun getAlarmsOwnedBy(ownerAccountId: String): List<AlarmEntity>

    @Query("""
        UPDATE alarms SET owner_account_id = :ownerAccountId
        WHERE id IN (:alarmIds) AND owner_account_id IS NULL
            AND NOT EXISTS (
                SELECT 1 FROM alarm_occurrence_sync e
                WHERE e.alarm_id = alarms.id AND e.owner_account_id != :ownerAccountId
            )
            AND NOT EXISTS (
                SELECT 1 FROM ios_alarm_occurrence_recording d
                WHERE d.alarm_id = alarms.id AND d.owner_account_id IS NOT NULL
                    AND d.owner_account_id != :ownerAccountId
            )
    """)
    suspend fun bindLegacyAlarms(alarmIds: List<String>, ownerAccountId: String): Int

    @Transaction
    suspend fun assignUnownedLegacyAlarms(ownerAccountId: String): List<AlarmEntity> {
        require(ownerAccountId.isNotBlank())
        val candidates = getEligibleUnownedLegacyAlarms(ownerAccountId)
        if (candidates.isEmpty()) return emptyList()
        val updated = bindLegacyAlarms(candidates.map(AlarmEntity::id), ownerAccountId)
        check(updated == candidates.size) { "레거시 알람 소유자를 일괄 저장하지 못했습니다." }
        return candidates.map { it.copy(ownerAccountId = ownerAccountId) }
    }

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
    suspend fun migrateId(
        oldId: String,
        newId: String,
    ): Boolean {
        require(oldId.isNotBlank()) { "Old alarm id must not be blank." }
        require(newId.isNotBlank()) { "New alarm id must not be blank." }
        if (oldId == newId) return getById(oldId) != null
        val existing = getById(oldId) ?: return false
        check(getById(newId) == null) { "Alarm id already exists: $newId" }

        insertAlarm(existing.alarm.copy(id = newId))
        if (existing.repeatDays.isNotEmpty()) {
            insertRepeatDays(
                existing.repeatDays.map { repeatDay -> repeatDay.copy(alarmId = newId) },
            )
        }
        check(delete(oldId) == 1) { "Failed to remove migrated alarm: $oldId" }
        return true
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
