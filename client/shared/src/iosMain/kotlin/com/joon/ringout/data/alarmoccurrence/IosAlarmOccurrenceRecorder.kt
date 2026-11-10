package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.alarm.IosAlarmMissionEventDto
import com.joon.ringout.alarm.IosAlarmRecoveryOwnerKind
import com.joon.ringout.alarm.IosAlarmRecoveryAttemptDto
import com.joon.ringout.alarm.toMissionEvent
import com.joon.ringout.alarm.IosRingingAlarm
import com.joon.ringout.alarm.iosInitialRingingScheduledAt
import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarmactivity.AlarmActivityDao
import platform.Foundation.NSUUID

data class IosAlarmOccurrenceOwner(val accountId: String?, val captured: Boolean)

/** iOS 정책: 서버 startedAt도 기록 화면과 동일한 예정 시각을 사용한다.
 * 관찰 시각과 해제 시각은 원래 로컬 필드에 유지하며 미션 제한시간에는 이 정책을 적용하지 않는다. */
class IosAlarmOccurrenceRecorder internal constructor(
    private val dao: AlarmOccurrenceSyncDao,
    private val activityDao: AlarmActivityDao,
    private val alarms: AlarmDataSource,
) {
    internal suspend fun recordObserved(ringing: IosRingingAlarm, owner: String?, ownerCaptured: Boolean) {
        val id = ringing.occurrenceId ?: return
        val savedOwner = alarms.getById(ringing.alarmId)?.request
            ?.takeIf { it.scheduleVersion == ringing.scheduleVersion }
            ?.ownerAccountId
        val reservationOwner = savedOwner ?: owner?.takeIf { ownerCaptured && it.isNotBlank() }
        recordRinging(
            id = id,
            alarmId = ringing.alarmId,
            version = ringing.scheduleVersion,
            retryAttempt = ringing.retryAttempt,
            referenceTime = ringing.startedAtEpochMillis,
            alarmTime = ringing.alarmTime,
            nativeOwner = reservationOwner,
            nativeOwnerCaptured = reservationOwner != null,
            chainId = ringing.chainId ?: id,
        )
    }

    internal suspend fun recordStopped(event: IosAlarmMissionEventDto): Boolean {
        val saved = alarms.getById(event.alarmId)?.request?.takeIf { it.scheduleVersion == event.scheduleVersion }
        val reservationOwner = saved?.ownerAccountId
            ?: event.ownerAccountId?.takeIf { event.ownerCaptured && it.isNotBlank() }
        val capturedGuest = saved?.ownerAccountId == null && event.ownerCaptured && event.ownerAccountId == null
        val ready = recordRinging(event.occurrenceId, event.alarmId, event.scheduleVersion, event.retryAttempt,
            event.ringingObservedAtEpochMillis ?: event.ringingStoppedAtEpochMillis ?: event.occurredAtEpochMillis,
            saved?.time, reservationOwner, reservationOwner != null,
            nativeOwnerIsGuest = capturedGuest,
            chainId = event.chainId ?: event.occurrenceId)
        event.ringingStoppedAtEpochMillis?.let { at ->
            val execution = dao.findExecutionForRecording(event.occurrenceId) ?: return@let
            dao.recordDismissal(execution.ownerAccountId, event.occurrenceId, at)
        }
        return ready
    }

    internal suspend fun recordRecoveryAttempt(
        attempt: IosAlarmRecoveryAttemptDto,
        scheduledAtEpochMillis: Long?,
    ): Boolean {
        val event = attempt.toMissionEvent()
        val rootOccurrenceId = attempt.chainId
        val decision = dao.getIosRecordDecision(attempt.chainId)
        val existingExecution = dao.findExecutionForRecording(event.occurrenceId)
            ?: dao.findExecutionForRecording(rootOccurrenceId)
        if (decision != null && (
                decision.rootOccurrenceId != rootOccurrenceId ||
                    decision.alarmId != event.alarmId ||
                    decision.scheduleVersion != event.scheduleVersion
            )
        ) return false
        if (decision?.state == IosAlarmOccurrenceRecordState.LOCAL_ONLY) return true
        if (existingExecution == null && event.retryAttempt > 0 &&
            dao.getIosRecordDecision(attempt.chainId)?.state != IosAlarmOccurrenceRecordState.SERVER
        ) return false
        if (existingExecution != null && (
                existingExecution.localExecutionId != rootOccurrenceId ||
                    existingExecution.alarmId != event.alarmId ||
                    existingExecution.scheduleVersion != event.scheduleVersion
            )
        ) return false
        val ready = recordRinging(
            id = event.occurrenceId,
            alarmId = event.alarmId,
            version = event.scheduleVersion,
            retryAttempt = event.retryAttempt,
            referenceTime = event.ringingObservedAtEpochMillis
                ?: event.ringingStoppedAtEpochMillis
                ?: event.occurredAtEpochMillis,
            alarmTime = attempt.snapshot.originalTime,
            nativeOwner = event.ownerAccountId,
            nativeOwnerCaptured = event.ownerCaptured,
            nativeOwnerIsGuest = attempt.snapshot.owner.kind == IosAlarmRecoveryOwnerKind.CAPTURED_GUEST,
            chainId = attempt.chainId,
            scheduledAtEpochMillis = scheduledAtEpochMillis,
        )
        if (!ready) return false
        val execution = dao.findExecutionForRecording(event.occurrenceId)
            ?: dao.findExecutionForRecording(rootOccurrenceId)
            ?: return ready
        event.ringingStoppedAtEpochMillis?.let { at ->
            dao.recordDismissal(execution.ownerAccountId, event.occurrenceId, at)
        }
        if (dao.getIosRecordDecision(attempt.chainId)?.state == IosAlarmOccurrenceRecordState.LOCAL_ONLY) return true
        if (event.retryAttempt > 0 && dao.findExecutionForRecording(event.occurrenceId) == null) return false
        dao.registerRepeat(execution.ownerAccountId, execution.localExecutionId,
            attempt.nextOccurrenceToken, NSUUID().UUIDString)
        return dao.findExecutionForRecording(event.occurrenceId) != null
    }

    /** Resolve pending records only from a matching, persisted reservation owner. */
    internal suspend fun resolvePendingRecordDecisions() {
        val pending = dao.getPendingIosRecordDecisions()
        if (pending.isEmpty()) return
        for (decision in pending) {
            val owner = alarms.getById(decision.alarmId)?.request
                ?.takeIf { it.scheduleVersion == decision.scheduleVersion }
                ?.ownerAccountId
                ?.takeIf(String::isNotBlank)
                ?: continue
            val execution = AlarmOccurrenceSyncEntity(
                localExecutionId = decision.rootOccurrenceId,
                ownerAccountId = owner,
                alarmId = decision.alarmId,
                scheduleVersion = decision.scheduleVersion,
                scheduledAtEpochMillis = decision.scheduledAtEpochMillis,
                startedAtEpochMillis = decision.startedAtEpochMillis,
            )
            if (dao.recordIosStart(decision, execution)) {
                val stoppedAt = activityDao.getOccurrenceTimes(decision.rootOccurrenceId)
                    ?.ringingStoppedAtEpochMillis
                if (stoppedAt != null) {
                    dao.recordDismissal(owner, decision.rootOccurrenceId, stoppedAt)
                }
            }
        }
    }

    private suspend fun recordRinging(
        id: String, alarmId: String, version: Long, retryAttempt: Int, referenceTime: Long,
        alarmTime: String?, nativeOwner: String?, nativeOwnerCaptured: Boolean,
        chainId: String = id,
        scheduledAtEpochMillis: Long? = null,
        nativeOwnerIsGuest: Boolean = false,
    ): Boolean {
        val existing = dao.findExecutionForRecording(id)
        val scheduledAt = scheduledAtEpochMillis
            ?: activityDao.getOccurrenceTimes(id)?.ringingScheduledAtEpochMillis
            ?: if (retryAttempt == 0) alarmTime?.let { iosInitialRingingScheduledAt(it, referenceTime) } else null
        if (retryAttempt == 0) {
            val record = IosAlarmOccurrenceRecordEntity(
                chainId = chainId,
                rootOccurrenceId = id,
                state = IosAlarmOccurrenceRecordState.AUTH_PENDING,
                ownerAccountId = null,
                alarmId = alarmId,
                scheduleVersion = version,
                scheduledAtEpochMillis = scheduledAt,
                startedAtEpochMillis = scheduledAt,
            )
            val disposition = chooseRecordDisposition(
                record, existing, nativeOwner, nativeOwnerCaptured, nativeOwnerIsGuest,
            )
            when (disposition) {
                RecordDisposition.LocalOnly -> {
                    dao.keepIosRecordLocalOnly(record)
                    return true
                }
                RecordDisposition.Pending -> {
                    dao.keepIosRecordPending(record)
                    return false
                }
                is RecordDisposition.Server -> {
                    return dao.recordIosStart(
                        record,
                        AlarmOccurrenceSyncEntity(id, disposition.ownerAccountId, alarmId, version, scheduledAt, scheduledAt),
                    )
                }
                is RecordDisposition.ExistingServer -> {
                    dao.recordIosStart(
                        record,
                        AlarmOccurrenceSyncEntity(id, disposition.ownerAccountId, alarmId, version, scheduledAt, scheduledAt),
                    )
                    return true
                }
            }
        } else {
            val rootDecision = dao.getIosRecordDecision(chainId)
            if (rootDecision?.state == IosAlarmOccurrenceRecordState.LOCAL_ONLY) return true
            val execution = existing ?: dao.findExecutionForRecording(rootDecision?.rootOccurrenceId ?: return false) ?: return false
            if (execution.alarmId != alarmId || execution.scheduleVersion != version ||
                execution.localExecutionId != chainId
            ) return false
            val link = dao.getRinging(execution.ownerAccountId, id) ?: return false
            dao.recordRepeat(execution.ownerAccountId, execution.localExecutionId, id,
                checkNotNull(link.eventId), scheduledAt)
            return true
        }
    }

    private suspend fun chooseRecordDisposition(
        record: IosAlarmOccurrenceRecordEntity,
        existingExecution: AlarmOccurrenceSyncEntity?,
        nativeOwner: String?,
        nativeOwnerCaptured: Boolean,
        nativeOwnerIsGuest: Boolean,
    ): RecordDisposition {
        val stored = dao.getIosRecordDecision(record.chainId)
        val execution = existingExecution ?: dao.findExecutionForRecording(record.rootOccurrenceId)
        if (execution != null || stored?.state == IosAlarmOccurrenceRecordState.SERVER) {
            return RecordDisposition.ExistingServer(
                stored?.ownerAccountId ?: execution?.ownerAccountId ?: return RecordDisposition.Pending,
            )
        }
        if (stored?.state == IosAlarmOccurrenceRecordState.LOCAL_ONLY) return RecordDisposition.LocalOnly
        if (nativeOwnerIsGuest) return RecordDisposition.LocalOnly
        val owner = nativeOwner?.takeIf { nativeOwnerCaptured && it.isNotBlank() }
            ?: return RecordDisposition.Pending
        return RecordDisposition.Server(owner)
    }

    private sealed interface RecordDisposition {
        data object LocalOnly : RecordDisposition
        data object Pending : RecordDisposition
        data class Server(val ownerAccountId: String) : RecordDisposition
        data class ExistingServer(val ownerAccountId: String) : RecordDisposition
    }

    internal suspend fun registerRetry(id: String, sourceId: String) {
        val execution = dao.findExecutionForRecording(sourceId) ?: return
        dao.registerRepeat(execution.ownerAccountId, execution.localExecutionId, id, NSUUID().UUIDString)
    }

    internal suspend fun fillRetrySchedule(id: String) {
        val execution = dao.findExecutionForRecording(id) ?: return
        // 해제 시각이 늦게 전달되면 이미 확인된 재울림만 보완한다. 아직 예약뿐인 알람은 전송하지 않는다.
        if (dao.getEvents(execution.ownerAccountId, execution.localExecutionId).none {
                it.localRingingId == id && it.kind == AlarmOccurrenceOutboxKind.REPEAT_RANG
            }) return
        val scheduledAt = activityDao.getOccurrenceTimes(id)?.ringingScheduledAtEpochMillis ?: return
        val link = dao.getRinging(execution.ownerAccountId, id) ?: return
        dao.recordRepeat(execution.ownerAccountId, execution.localExecutionId, id,
            checkNotNull(link.eventId), scheduledAt)
    }

    internal suspend fun recordTerminal(id: String, at: Long?, arrived: Boolean) {
        if (at == null) return
        val execution = dao.findExecutionForRecording(id) ?: return
        dao.recordTerminal(execution.ownerAccountId, id,
            if (arrived) AlarmOccurrenceOutboxKind.ARRIVED else AlarmOccurrenceOutboxKind.FORCE_ENDED, at)
    }
}
