package com.joon.ringout.domain.alarmoccurrence

interface AlarmOccurrenceRepository {
    /** 동일 실행 재요청 시에도 원래 alarmId와 scheduledAt, startedAt을 유지한다. */
    suspend fun start(start: AlarmOccurrenceStart): AlarmOccurrence

    suspend fun recordEvent(
        occurrenceId: AlarmOccurrenceId,
        event: AlarmOccurrenceEvent,
    ): AlarmOccurrence
}
