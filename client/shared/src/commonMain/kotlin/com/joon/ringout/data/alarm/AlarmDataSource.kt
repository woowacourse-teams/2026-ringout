package com.joon.ringout.data.alarm

import com.joon.ringout.alarm.SavedAlarmSchedule
import kotlinx.coroutines.flow.Flow

interface AlarmDataSource {
    fun observeAll(): Flow<List<SavedAlarmSchedule>>

    suspend fun getAll(): List<SavedAlarmSchedule>

    suspend fun getEnabled(): List<SavedAlarmSchedule>

    suspend fun getById(id: String): SavedAlarmSchedule?

    suspend fun replace(alarm: SavedAlarmSchedule)

    suspend fun replaceUserAlarm(alarm: SavedAlarmSchedule, previous: SavedAlarmSchedule?) = replace(alarm)

    suspend fun deleteUserAlarm(id: String): Boolean = delete(id)

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean

    suspend fun disableAll() {
        getAll().forEach { alarm ->
            check(setEnabled(alarm.request.id, false)) { "저장된 알람을 비활성화하지 못했습니다." }
        }
    }

    suspend fun delete(id: String): Boolean

    /** Atomically replaces a persisted alarm primary key while preserving its full snapshot. */
    suspend fun migrateId(
        oldId: String,
        newId: String,
    ): Boolean = false

    /** Keeps a marker on the new primary key until the native reservation is confirmed. */
    suspend fun migrateIdForReservation(oldId: String, newId: String): Boolean =
        migrateId(oldId, newId)

    suspend fun completeReservationIdMigration(id: String): Boolean = true

    suspend fun hasStorageMigration(id: String): Boolean

    suspend fun importLegacyIfNeeded(
        alarms: List<SavedAlarmSchedule>,
        migrationId: String,
        completedAtEpochMillis: Long,
    ): Boolean
}
