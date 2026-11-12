package com.joon.ringout.data.alarm

import androidx.room3.withWriteTransaction
import com.joon.ringout.data.database.RingoutDatabase
import kotlinx.serialization.json.Json

/** Local side of server synchronization. Multi-row changes run inside [transaction]. */
internal class AlarmSyncStore(
    private val database: RingoutDatabase,
    private val newDeviceAlarmId: () -> String,
) {
    private val alarms = database.alarmDao()
    private val sync = database.alarmSyncDao()

    val outbox = sync.observeOutbox()

    suspend fun <T> transaction(block: suspend () -> T): T = database.withWriteTransaction { block() }

    suspend fun pending(owner: String): List<AlarmOutboxEntity> = sync.getOutbox(owner)

    suspend fun restoreComplete(owner: String): Boolean = sync.getRestoreComplete(owner)

    suspend fun markRestoreComplete(owner: String) = sync.setRestoreComplete(owner)

    suspend fun resetRestore(owner: String) = sync.clearRestoreComplete(owner)

    suspend fun startAttempt(entry: AlarmOutboxEntity): AlarmOutboxEntity? {
        val current = entriesFor(entry).firstOrNull()
        if (current != entry) return null
        val attempted = entry.copy(
            attemptCount = (entry.attemptCount + 1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            payload = rebaseUnsentRequest(entry),
        )
        sync.updateOutbox(attempted)
        return attempted
    }

    private suspend fun rebaseUnsentRequest(entry: AlarmOutboxEntity): String? {
        if (entry.attemptCount > 0 || entry.payload == null) return entry.payload
        val request = Json.decodeFromString<RemoteAlarm>(entry.payload)
        val local = alarms.getOwnedByRemoteId(entry.ownerAccountId, entry.remoteAlarmId)?.alarm
            ?: return entry.payload
        val confirmed = local.confirmedAlarmVersion ?: return entry.payload
        if (request.alarmVersion > confirmed || confirmed == Long.MAX_VALUE) return entry.payload

        val version = confirmed + 1
        alarms.upsertAlarm(local.copy(alarmVersion = maxOf(local.alarmVersion, version)))
        return Json.encodeToString(request.copy(alarmVersion = version))
    }

    suspend fun retry(entry: AlarmOutboxEntity, at: Long, message: String) {
        if (entriesFor(entry).firstOrNull() == entry) {
            sync.updateOutbox(entry.copy(nextAttemptAt = at, lastError = message))
        }
    }

    suspend fun reject(entry: AlarmOutboxEntity, message: String) {
        val entries = entriesFor(entry)
        if (entries.firstOrNull() != entry) return
        if (entries.size > 1) {
            // A newer valid edit or delete already superseded this rejected request.
            sync.deleteOutbox(entry.id)
        } else {
            sync.updateOutbox(entry.copy(state = "REJECTED", nextAttemptAt = null, lastError = message))
        }
    }

    suspend fun acknowledgeDelete(entry: AlarmOutboxEntity) {
        if (entriesFor(entry).firstOrNull() == entry) sync.deleteOutbox(entry.id)
    }

    /**
     * A conflict with the last pending edit may need a native cancellation before committing.
     * Later local work takes precedence and keeps its payload and local reservation.
     */
    suspend fun acknowledgeUpsert(
        entry: AlarmOutboxEntity,
        request: RemoteAlarm,
        server: RemoteAlarm,
    ): AlarmWithRepeatDays? {
        val entries = entriesFor(entry)
        if (entries.firstOrNull() != entry) return null
        val local = alarms.getOwnedByRemoteId(entry.ownerAccountId, entry.remoteAlarmId)
        if (local != null) {
            val confirmed = local.alarm.confirmedAlarmVersion
            if (confirmed == null || server.alarmVersion >= confirmed) {
                if (entries.size == 1 && !request.hasSameSettings(server)) return local
                recordConfirmedVersion(local, server)
            }
        }
        sync.deleteOutbox(entry.id)
        return null
    }

    suspend fun applyUpsertConflict(
        entry: AlarmOutboxEntity,
        expected: AlarmWithRepeatDays,
        server: RemoteAlarm,
    ): Boolean {
        if (entriesFor(entry).singleOrNull() != entry ||
            alarms.getById(expected.alarm.id) != expected
        ) return false

        replaceWithServer(server, entry.ownerAccountId, expected.alarm, keepDevicePreferences = true)
        sync.deleteOutbox(entry.id)
        return true
    }

    suspend fun applySnapshotWithoutChangingReservations(
        owner: String,
        snapshot: List<RemoteAlarm>,
    ): List<AlarmServerChange> {
        val remoteById = snapshot.associateBy(RemoteAlarm::alarmId)
        val localById = alarms.getOwnedWithRepeatDays(owner).associateBy { it.alarm.syncId }
        val pendingIds = pending(owner).mapTo(mutableSetOf(), AlarmOutboxEntity::remoteAlarmId)
        val deferred = mutableListOf<AlarmServerChange>()

        for ((remoteId, server) in remoteById) {
            val local = localById[remoteId]
            val confirmed = local?.alarm?.confirmedAlarmVersion
            if (confirmed != null && server.alarmVersion < confirmed) continue

            when {
                remoteId in pendingIds -> if (local != null) recordConfirmedVersion(local, server)
                local == null -> replaceWithServer(server, owner)
                local.toRemoteAlarm(
                    alarmId = remoteId,
                    alarmVersion = server.alarmVersion,
                    repeatTime = local.alarm.remoteRepeatTime ?: local.alarm.limitMinutes,
                ).hasSameSettings(server) -> recordConfirmedVersion(local, server)
                else -> deferred += AlarmServerChange(local, server)
            }
        }

        for ((remoteId, local) in localById) {
            if (remoteId in remoteById || remoteId in pendingIds) continue
            if (local.alarm.confirmedAlarmVersion == null) {
                enqueueLegacyUpload(owner, local)
            } else {
                deferred += AlarmServerChange(local, remote = null)
            }
        }
        return deferred
    }

    suspend fun applyServerChange(owner: String, change: AlarmServerChange): Boolean {
        val expected = change.local.alarm
        if (pending(owner).any { it.remoteAlarmId == expected.syncId } ||
            alarms.getById(expected.id) != change.local
        ) return false

        if (change.remote == null) {
            alarms.delete(expected.id)
        } else {
            replaceWithServer(change.remote, owner, expected)
        }
        return true
    }

    private suspend fun enqueueLegacyUpload(owner: String, local: AlarmWithRepeatDays) {
        val existing = local.alarm
        val version = (existing.alarmVersion + 1).coerceAtLeast(1)
        val remote = local.toRemoteAlarm(
            alarmId = existing.syncId,
            alarmVersion = version,
            repeatTime = existing.remoteRepeatTime ?: existing.limitMinutes,
        )
        alarms.upsertAlarm(existing.copy(remoteAlarmId = remote.alarmId, alarmVersion = version))
        sync.insertOutbox(
            AlarmOutboxEntity(
                ownerAccountId = owner,
                remoteAlarmId = remote.alarmId,
                payload = Json.encodeToString(remote),
            ),
        )
    }

    private suspend fun recordConfirmedVersion(local: AlarmWithRepeatDays, server: RemoteAlarm) {
        val existing = local.alarm
        alarms.upsertAlarm(
            existing.copy(
                remoteAlarmId = server.alarmId,
                confirmedAlarmVersion = server.alarmVersion,
            ),
        )
    }

    private suspend fun replaceWithServer(
        server: RemoteAlarm,
        owner: String,
        previous: AlarmEntity? = null,
        keepDevicePreferences: Boolean = false,
    ) {
        val restored = server.toAlarmWithRepeatDays(previous?.id ?: newDeviceAlarmId(), owner)
        val localPreferences = previous?.takeIf { keepDevicePreferences }
        val localLabel = previous?.takeIf {
            it.destinationLatitude == server.destinationLatitude &&
                it.destinationLongitude == server.destinationLongitude &&
                server.copy(destinationName = it.destinationLabel).normalized().destinationName ==
                server.destinationName
        }?.destinationLabel
        alarms.replace(
            restored.copy(
                alarm = restored.alarm.copy(
                    destinationLabel = localLabel ?: restored.alarm.destinationLabel,
                    alarmSoundName = localPreferences?.alarmSoundName ?: restored.alarm.alarmSoundName,
                    alarmSoundUri = localPreferences?.alarmSoundUri,
                    targetDistanceKm = localPreferences?.targetDistanceKm ?: restored.alarm.targetDistanceKm,
                    // Native recovery snapshots are immutable for a given reservation ID and version.
                    scheduleVersion = previous?.let { it.scheduleVersion + 1 } ?: restored.alarm.scheduleVersion,
                    remoteAlarmId = server.alarmId,
                    alarmVersion = maxOf(previous?.alarmVersion ?: 0L, server.alarmVersion),
                    confirmedAlarmVersion = server.alarmVersion,
                    remoteRepeatTime = server.repeatTime,
                    pendingReservationId = previous?.pendingReservationId,
                ),
            ),
        )
    }

    private suspend fun entriesFor(entry: AlarmOutboxEntity): List<AlarmOutboxEntity> =
        pending(entry.ownerAccountId).filter { it.remoteAlarmId == entry.remoteAlarmId }

    private val AlarmEntity.syncId: String get() = remoteAlarmId ?: id

    private fun RemoteAlarm.hasSameSettings(other: RemoteAlarm): Boolean =
        normalized() == other.normalized().copy(alarmVersion = alarmVersion)
}

internal data class AlarmServerChange(
    val local: AlarmWithRepeatDays,
    val remote: RemoteAlarm?,
)
