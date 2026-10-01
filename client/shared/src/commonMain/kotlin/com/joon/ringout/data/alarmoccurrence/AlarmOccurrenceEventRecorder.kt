package com.joon.ringout.data.alarmoccurrence

import kotlinx.serialization.Serializable

/** 발생 시점에 캡처한 값만 저장한다. 복구 시점의 로그인 계정이나 현재 시각으로 보정하지 않는다. */
@Serializable
internal sealed interface CapturedAlarmOccurrenceEvent {
    val ringingId: String

    @Serializable
    data class Rang(
        override val ringingId: String,
        val alarmId: String,
        val scheduleVersion: Long,
        val scheduledAt: Long?,
        val at: Long,
        val ownerAccountId: String?,
        val sourceRingingId: String? = null,
        val eventId: String,
    ) : CapturedAlarmOccurrenceEvent

    @Serializable
    data class Dismissed(override val ringingId: String, val at: Long) : CapturedAlarmOccurrenceEvent

    @Serializable
    data class Terminal(override val ringingId: String, val at: Long?, val arrived: Boolean) : CapturedAlarmOccurrenceEvent
}

/** 플랫폼의 영속 저널을 순서대로 Room outbox로 옮긴다. HTTP 응답을 기다리지 않는다. */
internal class AlarmOccurrenceEventRecorder(private val dao: AlarmOccurrenceSyncDao) {
    suspend fun record(event: CapturedAlarmOccurrenceEvent) {
        when (event) {
            is CapturedAlarmOccurrenceEvent.Rang -> {
                if (event.sourceRingingId == null) {
                    val owner = event.ownerAccountId ?: return // 게스트 기록은 이후 로그인해도 전송하지 않는다.
                    val existing = dao.findExecutionForRecording(event.ringingId)
                    dao.recordStart(AlarmOccurrenceSyncEntity(
                        localExecutionId = event.ringingId,
                        ownerAccountId = existing?.ownerAccountId ?: owner,
                        alarmId = event.alarmId,
                        scheduleVersion = event.scheduleVersion,
                        scheduledAtEpochMillis = event.scheduledAt,
                        startedAtEpochMillis = event.at,
                    ))
                } else {
                    val execution = dao.findExecutionForRecording(event.sourceRingingId) ?: return
                    dao.recordRepeat(execution.ownerAccountId, execution.localExecutionId,
                        event.ringingId, event.eventId, event.at)
                }
            }
            is CapturedAlarmOccurrenceEvent.Dismissed -> {
                val execution = dao.findExecutionForRecording(event.ringingId) ?: return
                dao.recordDismissal(execution.ownerAccountId, event.ringingId, event.at)
            }
            is CapturedAlarmOccurrenceEvent.Terminal -> {
                // 기능 도입 전 미션은 정확한 종료 시각/최초 울림이 없을 수 있다.
                val at = event.at ?: return
                val execution = dao.findExecutionForRecording(event.ringingId) ?: return
                dao.recordTerminal(execution.ownerAccountId, event.ringingId,
                    if (event.arrived) AlarmOccurrenceOutboxKind.ARRIVED else AlarmOccurrenceOutboxKind.FORCE_ENDED, at)
            }
        }
    }
}
