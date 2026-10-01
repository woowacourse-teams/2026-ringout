package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.alarm.IosAlarmMissionEventDto
import com.joon.ringout.alarm.IosRingingAlarm
import com.joon.ringout.alarm.iosInitialRingingScheduledAt
import com.joon.ringout.data.alarm.AlarmDataSource
import com.joon.ringout.data.alarmactivity.AlarmActivityDao
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import platform.Foundation.NSUUID

/** 네이티브 파일을 기록하는 순간 계정을 고정한다. 토큰은 네이티브 저널에 남기지 않는다. */
object IosAlarmOccurrenceAccount {
    fun capture(): IosAlarmOccurrenceOwner = runCatching {
        IosAlarmOccurrenceOwner(createSecureTokenStorage().readSnapshot()?.accessToken
            ?.let(::alarmOccurrenceTokenOwner), captured = true)
    }.getOrElse { IosAlarmOccurrenceOwner(null, captured = false) }
}

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
        recordRinging(id, ringing.alarmId, ringing.scheduleVersion, ringing.retryAttempt,
            ringing.startedAtEpochMillis, ringing.alarmTime, owner, ownerCaptured)
    }

    internal suspend fun recordStopped(event: IosAlarmMissionEventDto) {
        val saved = alarms.getById(event.alarmId)?.request?.takeIf { it.scheduleVersion == event.scheduleVersion }
        recordRinging(event.occurrenceId, event.alarmId, event.scheduleVersion, event.retryAttempt,
            event.ringingObservedAtEpochMillis ?: event.ringingStoppedAtEpochMillis ?: event.occurredAtEpochMillis,
            saved?.time, event.ownerAccountId, event.ownerCaptured)
        event.ringingStoppedAtEpochMillis?.let { at ->
            val execution = dao.findExecutionForRecording(event.occurrenceId) ?: return@let
            dao.recordDismissal(execution.ownerAccountId, event.occurrenceId, at)
        }
    }

    private suspend fun recordRinging(
        id: String, alarmId: String, version: Long, retryAttempt: Int, referenceTime: Long,
        alarmTime: String?, owner: String?, ownerCaptured: Boolean,
    ) {
        val existing = dao.findExecutionForRecording(id)
        val scheduledAt = activityDao.getOccurrenceTimes(id)?.ringingScheduledAtEpochMillis
            ?: if (retryAttempt == 0) alarmTime?.let { iosInitialRingingScheduledAt(it, referenceTime) } else null
        if (retryAttempt == 0) {
            val capturedOwner = existing?.ownerAccountId ?: owner.takeIf { ownerCaptured } ?: return
            dao.recordStart(AlarmOccurrenceSyncEntity(id, capturedOwner, alarmId, version, scheduledAt, scheduledAt))
        } else {
            // 재울림 예약 시 저장한 연결로 최초 계정을 찾는다. 현재 로그인 계정으로 새 실행을 만들지 않는다.
            val execution = existing ?: return
            val link = dao.getRinging(execution.ownerAccountId, id) ?: return
            dao.recordRepeat(execution.ownerAccountId, execution.localExecutionId, id,
                checkNotNull(link.eventId), scheduledAt)
        }
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
