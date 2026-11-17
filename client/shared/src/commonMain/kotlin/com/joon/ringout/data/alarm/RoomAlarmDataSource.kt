package com.joon.ringout.data.alarm

import androidx.room3.withWriteTransaction
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.destination.DestinationAccountProvider
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAlarmDataSource private constructor(
    private val alarmDao: AlarmDao,
    private val database: RingoutDatabase?,
    private val accountProvider: DestinationAccountProvider?,
) : AlarmDataSource {
    constructor(alarmDao: AlarmDao) : this(alarmDao, null, null)

    constructor(database: RingoutDatabase, accountProvider: DestinationAccountProvider? = null) :
        this(database.alarmDao(), database, accountProvider)

    override fun observeAll(): Flow<List<SavedAlarmSchedule>> {
        val accounts = accountProvider?.accountIds
        val alarms = if (accounts == null) alarmDao.observeAll() else {
            alarmDao.observeAll().combine(accounts) { rows, account -> rows.filter { it.isVisibleTo(account) } }
        }
        return alarms.map { rows -> rows.map(AlarmWithRepeatDays::toSavedAlarmSchedule) }
    }

    override suspend fun getAll(): List<SavedAlarmSchedule> =
        visible(alarmDao.getAll()).map(AlarmWithRepeatDays::toSavedAlarmSchedule)

    override suspend fun getEnabled(): List<SavedAlarmSchedule> =
        visible(alarmDao.getEnabled()).map(AlarmWithRepeatDays::toSavedAlarmSchedule)

    override suspend fun getById(id: String): SavedAlarmSchedule? =
        visible(listOfNotNull(alarmDao.getById(id))).singleOrNull()?.toSavedAlarmSchedule()

    private suspend fun visible(rows: List<AlarmWithRepeatDays>): List<AlarmWithRepeatDays> =
        if (accountProvider == null) rows else {
            val account = accountProvider.currentAccountId()
            rows.filter { it.isVisibleTo(account) }
        }

    private fun AlarmWithRepeatDays.isVisibleTo(account: String?) =
        alarm.ownerAccountId == null || alarm.ownerAccountId == account

    override suspend fun replace(alarm: SavedAlarmSchedule) {
        val next = alarm.toAlarmWithRepeatDays()
        val db = database
        if (db == null) alarmDao.replace(next)
        else db.withWriteTransaction {
            val stored = alarmDao.getById(next.alarm.id)?.alarm
            alarmDao.replace(next.withSyncMetadata(stored))
        }
    }

    override suspend fun replaceUserAlarm(alarm: SavedAlarmSchedule, previous: SavedAlarmSchedule?) {
        val db = database ?: return replace(alarm)
        db.withWriteTransaction {
            val next = alarm.toAlarmWithRepeatDays()
            val stored = alarmDao.getById(next.alarm.id)?.alarm
            val owner = stored?.ownerAccountId ?: next.alarm.ownerAccountId
            val merged = next.withSyncMetadata(stored).let { it.copy(alarm = it.alarm.copy(ownerAccountId = owner)) }
            if (owner == null) {
                alarmDao.replace(merged)
                return@withWriteTransaction
            }
            check(accountProvider == null || accountProvider.currentAccountId() == owner)
            val remoteId = merged.alarm.remoteAlarmId ?: merged.alarm.id
            val repeatTime = if (previous?.request?.limitMinutes == alarm.request.limitMinutes) {
                stored?.remoteRepeatTime ?: next.alarm.limitMinutes
            } else next.alarm.limitMinutes
            val version = maxOf(merged.alarm.alarmVersion, merged.alarm.confirmedAlarmVersion ?: 0)
            val payload = merged.toRemoteAlarm(remoteId, version, repeatTime)
            val old = previous?.toAlarmWithRepeatDays()?.toRemoteAlarm(
                remoteId,
                version,
                stored?.remoteRepeatTime ?: previous.request.limitMinutes,
            )
            val changed = payload != old
            val newVersion = if (changed) {
                check(version < Long.MAX_VALUE) { "알람 버전을 더 높일 수 없습니다." }
                version + 1
            } else merged.alarm.alarmVersion
            alarmDao.replace(merged.copy(alarm = merged.alarm.copy(
                remoteAlarmId = remoteId,
                alarmVersion = newVersion,
                remoteRepeatTime = repeatTime,
            )))
            if (changed) {
                val syncDao = db.alarmSyncDao()
                syncDao.deleteRejected(owner, remoteId)
                syncDao.insertOutbox(AlarmOutboxEntity(
                    ownerAccountId = owner,
                    remoteAlarmId = remoteId,
                    payload = Json.encodeToString(RemoteAlarm.serializer(), payload.copy(alarmVersion = newVersion)),
                ))
            }
        }
    }

    override suspend fun deleteUserAlarm(id: String): Boolean {
        val db = database ?: return delete(id)
        return db.withWriteTransaction {
            val stored = alarmDao.getById(id)?.alarm ?: return@withWriteTransaction false
            val owner = stored.ownerAccountId
            if (owner != null) {
                check(accountProvider == null || accountProvider.currentAccountId() == owner)
                val remoteId = stored.remoteAlarmId ?: stored.id
                val syncDao = db.alarmSyncDao()
                syncDao.deleteRejected(owner, remoteId)
                syncDao.insertOutbox(AlarmOutboxEntity(ownerAccountId = owner, remoteAlarmId = remoteId, payload = null))
            }
            alarmDao.delete(id) == 1
        }
    }

    private fun AlarmWithRepeatDays.withSyncMetadata(stored: AlarmEntity?): AlarmWithRepeatDays =
        if (stored == null) this else copy(alarm = alarm.copy(
            ownerAccountId = stored.ownerAccountId ?: alarm.ownerAccountId,
            remoteAlarmId = stored.remoteAlarmId ?: alarm.remoteAlarmId,
            alarmVersion = stored.alarmVersion,
            confirmedAlarmVersion = stored.confirmedAlarmVersion,
            remoteRepeatTime = stored.remoteRepeatTime,
            pendingReservationId = stored.pendingReservationId,
        ))

    override suspend fun setEnabled(id: String, enabled: Boolean): Boolean =
        alarmDao.setEnabled(id = id, enabled = enabled) == 1

    override suspend fun disableAll() {
        alarmDao.disableAll()
    }

    override suspend fun delete(id: String): Boolean = alarmDao.delete(id) == 1

    override suspend fun migrateId(oldId: String, newId: String): Boolean =
        alarmDao.migrateId(oldId = oldId, newId = newId)

    override suspend fun migrateIdForReservation(oldId: String, newId: String): Boolean =
        alarmDao.migrateId(oldId = oldId, newId = newId, keepReservationMarker = true)

    override suspend fun completeReservationIdMigration(id: String): Boolean =
        alarmDao.completeReservationIdMigration(id) == 1

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
