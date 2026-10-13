package com.joon.ringout.alarm

import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosAlarmNativeCleanupDtoTest {
    @Test
    fun `루트 중지 이벤트는 시작과 초기 해제 행을 요구한다`() {
        val request = candidate(
            retryAttempt = 0,
            action = IosAlarmMissionAction.STOP,
            stoppedAt = 2_000,
        ).roomReadinessRequest()

        assertEquals(
            listOf("start" to AlarmOccurrenceOutboxKind.START, "dismiss:root" to AlarmOccurrenceOutboxKind.INITIAL_DISMISSED),
            request?.expectedOutbox?.map { it.deduplicationKey to it.kind },
        )
    }

    @Test
    fun `실제 재울림 중지 이벤트는 재울림과 반복 해제 행을 요구한다`() {
        val request = candidate(
            retryAttempt = 1,
            action = IosAlarmMissionAction.OPEN_APP,
            observedAt = 1_500,
            stoppedAt = 2_000,
        ).roomReadinessRequest()

        assertEquals(
            listOf(
                "start" to AlarmOccurrenceOutboxKind.START,
                "repeat:retry" to AlarmOccurrenceOutboxKind.REPEAT_RANG,
                "dismiss:retry" to AlarmOccurrenceOutboxKind.REPEAT_DISMISSED,
            ),
            request?.expectedOutbox?.map { it.deduplicationKey to it.kind },
        )
    }

    @Test
    fun `기록 비대상 앱 열기 이벤트는 Room 기록을 요구하지 않는다`() {
        assertNull(candidate(
            retryAttempt = 0,
            action = IosAlarmMissionAction.OPEN_APP,
        ).roomReadinessRequest())
    }

    private fun candidate(
        retryAttempt: Int,
        action: IosAlarmMissionAction,
        observedAt: Long? = null,
        stoppedAt: Long? = null,
    ) = IosAlarmNativeCleanupEventCandidate(
        eventId = "event",
        fingerprint = "candidate-fingerprint",
        alarmId = "alarm",
        occurrenceId = if (retryAttempt == 0) "root" else "retry",
        action = action,
        occurredAtEpochMillis = 3_000,
        retryAttempt = retryAttempt,
        ringingObservedAtEpochMillis = observedAt,
        ringingStoppedAtEpochMillis = stoppedAt,
        scheduleVersion = 1,
        chainId = "root",
    )
}
