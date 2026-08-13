package com.joon.ringout.data.alarm

import com.joon.ringout.alarm.SavedAlarmSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAlarmDataSource(
    private val alarmDao: AlarmDao,
) : AlarmDataSource {
    override fun observeAll(): Flow<List<SavedAlarmSchedule>> =
        alarmDao.observeAll().map { alarms -> alarms.map(AlarmWithRepeatDays::toSavedAlarmSchedule) }

    override suspend fun getAll(): List<SavedAlarmSchedule> =
        alarmDao.getAll().map(AlarmWithRepeatDays::toSavedAlarmSchedule)

    override suspend fun getEnabled(): List<SavedAlarmSchedule> =
        alarmDao.getEnabled().map(AlarmWithRepeatDays::toSavedAlarmSchedule)

    override suspend fun getById(id: String): SavedAlarmSchedule? =
        alarmDao.getById(id)?.toSavedAlarmSchedule()

    override suspend fun replace(alarm: SavedAlarmSchedule) {
        alarmDao.replace(alarm.toAlarmWithRepeatDays())
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): Boolean =
        alarmDao.setEnabled(id = id, enabled = enabled) == 1

    override suspend fun delete(id: String): Boolean = alarmDao.delete(id) == 1

    override suspend fun migrateId(oldId: String, newId: String): Boolean =
        alarmDao.migrateId(oldId = oldId, newId = newId)

    override suspend fun hasStorageMigration(id: String): Boolean =
        alarmDao.hasStorageMigration(id)

    override suspend fun importLegacyIfNeeded(
        alarms: List<SavedAlarmSchedule>,
        migrationId: String,
        completedAtEpochMillis: Long,
    ): Boolean {
        require(migrationId.isNotBlank()) { "Storage migration id must not be blank." }
        require(completedAtEpochMillis >= 0L) {
            "Storage migration completion time must not be negative: $completedAtEpochMillis"
        }
        return alarmDao.importLegacyIfNeeded(
            alarms = alarms.map(SavedAlarmSchedule::toAlarmWithRepeatDays),
            migration = StorageMigrationEntity(
                id = migrationId,
                completedAtEpochMillis = completedAtEpochMillis,
            ),
        )
    }
}
