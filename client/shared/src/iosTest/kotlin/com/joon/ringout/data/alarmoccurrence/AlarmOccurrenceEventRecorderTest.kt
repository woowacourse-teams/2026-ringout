package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.database.RingoutDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmOccurrenceEventRecorderTest {
    @Test
    fun `계정 변경 후 재울림과 해제와 강제종료도 최초 계정의 실행에 기록한다`() = withRecorder { recorder, dao ->
        recorder.record(ring("root", "1", 1_000))
        recorder.record(CapturedAlarmOccurrenceEvent.Dismissed("root", 2_000))
        recorder.record(ring("retry-1", "2", 3_000, "root"))
        recorder.record(CapturedAlarmOccurrenceEvent.Dismissed("retry-1", 4_000))
        recorder.record(ring("retry-2", null, 5_000, "retry-1"))
        recorder.record(CapturedAlarmOccurrenceEvent.Dismissed("retry-2", 6_000))
        recorder.record(CapturedAlarmOccurrenceEvent.Terminal("retry-2", 7_000, arrived = false))

        assertEquals("root", dao.findExecutionForRecording("retry-2")?.localExecutionId)
        assertEquals("1", dao.findExecutionForRecording("retry-2")?.ownerAccountId)
        assertTrue(dao.getUnsentEvents("2").isEmpty())
        assertEquals(listOf(AlarmOccurrenceOutboxKind.START, AlarmOccurrenceOutboxKind.INITIAL_DISMISSED,
            AlarmOccurrenceOutboxKind.REPEAT_RANG, AlarmOccurrenceOutboxKind.REPEAT_DISMISSED,
            AlarmOccurrenceOutboxKind.REPEAT_RANG, AlarmOccurrenceOutboxKind.REPEAT_DISMISSED,
            AlarmOccurrenceOutboxKind.FORCE_ENDED), dao.getEvents("1", "root").map { it.kind })
    }

    @Test
    fun `게스트 울림은 이후 로그인한 상태로 재울려도 서버 전송 기록을 만들지 않는다`() = withRecorder { recorder, dao ->
        recorder.record(ring("guest", null, 1_000))
        recorder.record(ring("retry", "1", 2_000, "guest"))
        recorder.record(CapturedAlarmOccurrenceEvent.Dismissed("retry", 3_000))
        recorder.record(CapturedAlarmOccurrenceEvent.Terminal("retry", 4_000, arrived = true))
        assertNull(dao.findExecutionForRecording("guest"))
        assertNull(dao.findExecutionForRecording("retry"))
        assertTrue(dao.getUnsentEvents("1").isEmpty())
    }

    @Test
    fun `중복 콜백은 최초 시각과 재울림 아이디를 유지하고 다음 날 울림은 별도 실행을 만든다`() = withRecorder { recorder, dao ->
        recorder.record(ring("day-1", "1", 1_000))
        recorder.record(ring("day-1", "2", 9_000))
        recorder.record(CapturedAlarmOccurrenceEvent.Dismissed("day-1", 2_000))
        recorder.record(CapturedAlarmOccurrenceEvent.Dismissed("day-1", 9_000))
        recorder.record(ring("retry", null, 3_000, "day-1"))
        recorder.record(ring("retry", null, 9_000, "day-1").copy(eventId = "another-id"))
        recorder.record(CapturedAlarmOccurrenceEvent.Terminal("retry", 4_000, arrived = true))
        recorder.record(CapturedAlarmOccurrenceEvent.Terminal("retry", 9_000, arrived = false))
        recorder.record(ring("day-2", "2", 10_000))

        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L), dao.getEvents("1", "day-1").map { it.occurredAtEpochMillis })
        assertEquals("event-retry", dao.getRinging("1", "retry")?.eventId)
        assertEquals(AlarmOccurrenceOutboxKind.ARRIVED, dao.getEvents("1", "day-1").last().kind)
        assertEquals(1, dao.getEvents("2", "day-2").size)
    }

    @Test
    fun `예약 시각이 없는 울림과 종료 시각이 없는 이전 미션은 현재 시각으로 보정하지 않는다`() = withRecorder { recorder, dao ->
        recorder.record(ring("root", "1", 1_000).copy(scheduledAt = null))
        recorder.record(CapturedAlarmOccurrenceEvent.Terminal("root", null, arrived = true))
        recorder.record(CapturedAlarmOccurrenceEvent.Terminal("legacy", 9_000, arrived = false))
        assertNull(dao.getExecution("1", "root")?.scheduledAtEpochMillis)
        assertEquals(1, dao.getEvents("1", "root").size)
        assertNull(dao.findExecutionForRecording("legacy"))
    }

    private fun withRecorder(block: suspend (AlarmOccurrenceEventRecorder, AlarmOccurrenceSyncDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
        try {
            val dao = db.alarmOccurrenceSyncDao()
            block(AlarmOccurrenceEventRecorder(dao), dao)
        } finally { db.close() }
    }
}

private fun ring(id: String, owner: String?, at: Long, source: String? = null) =
    CapturedAlarmOccurrenceEvent.Rang(id, "alarm-1", 1, at - 100, at, owner, source, "event-$id")
