package com.joon.ringout.data.alarm

import com.joon.ringout.alarm.SavedAlarmSchedule
import kotlinx.coroutines.flow.Flow

interface AlarmDataSource {
    fun observeAll(): Flow<List<SavedAlarmSchedule>>

    suspend fun getAll(): List<SavedAlarmSchedule>

    suspend fun getEnabled(): List<SavedAlarmSchedule>

    suspend fun getById(id: String): SavedAlarmSchedule?

    suspend fun replace(alarm: SavedAlarmSchedule)

    suspend fun setEnabled(id: String, enabled: Boolean): Boolean

    suspend fun delete(id: String): Boolean

    /** Atomically replaces a persisted alarm primary key while preserving its full snapshot. */
    suspend fun migrateId(
        oldId: String,
        newId: String,
    ): Boolean = false

    suspend fun hasStorageMigration(id: String): Boolean

    suspend fun importLegacyIfNeeded(
        alarms: List<SavedAlarmSchedule>,
        migrationId: String,
        completedAtEpochMillis: Long,
    ): Boolean
}
