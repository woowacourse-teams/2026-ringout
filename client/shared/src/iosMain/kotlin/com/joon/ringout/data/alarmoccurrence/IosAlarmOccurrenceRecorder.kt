package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.alarm.IosAlarmMissionEventDto
import com.joon.ringout.alarm.IosAlarmRecoveryAttemptDto
import com.joon.ringout.alarm.toMissionEvent
import com.joon.ringout.alarm.IosRingingAlarm
import com.joon.ringout.alarm.iosInitialRingingScheduledAt
import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarmactivity.AlarmActivityDao
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import platform.Foundation.NSUUID

/** Native snapshots retain only a guest marker; server ownership is resolved from AuthSession in the app. */
object IosAlarmOccurrenceAccount {
    fun capture(): IosAlarmOccurrenceOwner = runCatching {
        val token = createSecureTokenStorage().readSnapshot()?.accessToken
        val accountId = token?.let(::alarmOccurrenceTokenOwner)
        IosAlarmOccurrenceOwner(accountId, captured = token.isNullOrBlank() || accountId != null)
    }.getOrElse { IosAlarmOccurrenceOwner(null, captured = false) }
}

data class IosAlarmOccurrenceOwner(val accountId: String?, val captured: Boolean)

/** iOS 정책: 서버 startedAt도 기록 화면과 동일한 예정 시각을 사용한다.
 * 관찰 시각과 해제 시각은 원래 로컬 필드에 유지하며 미션 제한시간에는 이 정책을 적용하지 않는다. */
class IosAlarmOccurrenceRecorder internal constructor(
    private val dao: AlarmOccurrenceSyncDao,
    private val activityDao: AlarmActivityDao,
    private val alarms: AlarmDataSource,
    private val tokenStorage: SecureTokenStorage,
    private val session: AuthSession,
) {
    internal suspend fun recordObserved(ringing: IosRingingAlarm, owner: String?, ownerCaptured: Boolean) {
        val id = ringing.occurrenceId ?: return
        recordRinging(
            id = id,
            alarmId = ringing.alarmId,
            version = ringing.scheduleVersion,
            retryAttempt = ringing.retryAttempt,
            referenceTime = ringing.startedAtEpochMillis,
            alarmTime = ringing.alarmTime,
            nativeOwner = owner,
            nativeOwnerCaptured = ownerCaptured,
            chainId = ringing.chainId ?: id,
        )
    }

    internal suspend fun recordStopped(event: IosAlarmMissionEventDto): Boolean {
        val saved = alarms.getById(event.alarmId)?.request?.takeIf { it.scheduleVersion == event.scheduleVersion }
        val ready = recordRinging(event.occurrenceId, event.alarmId, event.scheduleVersion, event.retryAttempt,
            event.ringingObservedAtEpochMillis ?: event.ringingStoppedAtEpochMillis ?: event.occurredAtEpochMillis,
            saved?.time, event.ownerAccountId, event.ownerCaptured,
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
        if (decision?.state == IosAlarmOccurrenceRecordState.LOCAL_ONLY) return true
        if (existingExecution == null && event.retryAttempt > 0) {
            resolvePendingRecordDecisions()
            val afterResolution = dao.getIosRecordDecision(attempt.chainId)
            if (afterResolution?.state == IosAlarmOccurrenceRecordState.LOCAL_ONLY) return true
            if (afterResolution?.state != IosAlarmOccurrenceRecordState.SERVER ||
                dao.findExecutionForRecording(rootOccurrenceId) == null
            ) return false
        }
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

    /** Resolve only persisted record decisions; this never schedules or starts a mission. */
    internal suspend fun resolvePendingRecordDecisions() {
        val pending = dao.getPendingIosRecordDecisions()
        if (pending.isEmpty()) return
        when (session.state.value) {
            AuthSessionState.Unauthenticated -> {
                for (decision in pending) dao.keepIosRecordLocalOnly(decision)
            }
            AuthSessionState.Authenticated -> {
                val account = captureAlarmOccurrenceAccount(tokenStorage, session) ?: return
                for (decision in pending) {
                    try {
                        account.checkSession(session)
                    } catch (_: AlarmOccurrenceAccountChanged) {
                        return
                    }
                    val execution = AlarmOccurrenceSyncEntity(
                        localExecutionId = decision.rootOccurrenceId,
                        ownerAccountId = account.ownerAccountId,
                        alarmId = decision.alarmId,
                        scheduleVersion = decision.scheduleVersion,
                        scheduledAtEpochMillis = decision.scheduledAtEpochMillis,
                        startedAtEpochMillis = decision.startedAtEpochMillis,
                    )
                    if (dao.recordIosStart(decision, execution)) {
                        val stoppedAt = activityDao.getOccurrenceTimes(decision.rootOccurrenceId)
                            ?.ringingStoppedAtEpochMillis
                        if (stoppedAt != null) dao.recordDismissal(account.ownerAccountId, decision.rootOccurrenceId, stoppedAt)
                    }
                }
            }
            AuthSessionState.Restoring,
            AuthSessionState.ReauthenticationRequired -> Unit
        }
    }

    private suspend fun recordRinging(
        id: String, alarmId: String, version: Long, retryAttempt: Int, referenceTime: Long,
        alarmTime: String?, nativeOwner: String?, nativeOwnerCaptured: Boolean,
        chainId: String = id,
        scheduledAtEpochMillis: Long? = null,
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
            val disposition = chooseRecordDisposition(record, existing, nativeOwner, nativeOwnerCaptured)
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
                    try {
                        disposition.account.checkSession(session)
                    } catch (_: AlarmOccurrenceAccountChanged) {
                        dao.keepIosRecordPending(record)
                        return false
                    }
                    return dao.recordIosStart(
                        record,
                        AlarmOccurrenceSyncEntity(id, disposition.account.ownerAccountId, alarmId, version, scheduledAt, scheduledAt),
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
    ): RecordDisposition {
        val stored = dao.getIosRecordDecision(record.chainId)
        val execution = existingExecution ?: dao.findExecutionForRecording(record.rootOccurrenceId)
        if (execution != null || stored?.state == IosAlarmOccurrenceRecordState.SERVER) {
            return RecordDisposition.ExistingServer(
                stored?.ownerAccountId ?: execution?.ownerAccountId ?: return RecordDisposition.Pending,
            )
        }
        if (stored?.state == IosAlarmOccurrenceRecordState.LOCAL_ONLY) return RecordDisposition.LocalOnly
        // A previously captured guest remains local-only. Captured account IDs do not select server ownership.
        if (nativeOwnerCaptured && nativeOwner == null) return RecordDisposition.LocalOnly
        return when (session.state.value) {
            AuthSessionState.Unauthenticated -> RecordDisposition.LocalOnly
            AuthSessionState.Authenticated -> captureAlarmOccurrenceAccount(tokenStorage, session)
                ?.let(RecordDisposition::Server)
                ?: RecordDisposition.Pending
            AuthSessionState.Restoring,
            AuthSessionState.ReauthenticationRequired -> RecordDisposition.Pending
        }
    }

    private sealed interface RecordDisposition {
        data object LocalOnly : RecordDisposition
        data object Pending : RecordDisposition
        data class Server(val account: AlarmOccurrenceAccount) : RecordDisposition
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
