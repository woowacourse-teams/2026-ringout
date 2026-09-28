package com.joon.ringout.data.alarmactivity

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.missionhistory.MissionHistoryDto
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmOccurrenceTimesTest {
    @Test
    fun `이벤트 저장 순서가 바뀌어도 같은 실행의 세 시각을 합치고 재전달로 덮어쓰지 않는다`() = withDatabase { db ->
        val activity = db.alarmActivityDao()
        val history = RoomMissionHistoryDataSource(db.missionHistoryDao())
        activity.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity("one", ringingStoppedAtEpochMillis = 2_000))
        activity.record(AlarmActivityEntity.rang("alarm", "one", AlarmActivityTimestamp(1_000, "2026-09-27")))
        history.record(MissionHistoryDto("SUCCESS", "2026-09-28", "one", missionCompletedAtEpochMillis = 3_000))

        activity.record(AlarmActivityEntity.rang("alarm", "one", AlarmActivityTimestamp(9_000, "2026-09-28")))
        activity.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity("one", ringingStoppedAtEpochMillis = 9_000))
        history.record(MissionHistoryDto("SUCCESS", "2026-09-28", "one", missionCompletedAtEpochMillis = 9_000))
        val result = history.getHistory(MissionYearMonth(2026, 9)).single()

        assertEquals(1_000L, result.ringingStartedAtEpochMillis)
        assertEquals(2_000L, result.ringingStoppedAtEpochMillis)
        assertEquals(3_000L, result.missionCompletedAtEpochMillis)
        assertEquals(1, activity.observeCounts("2026-09-27").first().ringingCount)
        assertEquals(0, activity.observeCounts("2026-09-28").first().ringingCount)
    }

    @Test
    fun `같은 알람의 재울림은 별도 실행으로 연결하고 기존 기록의 없는 시각은 비워 둔다`() = withDatabase { db ->
        val activity = db.alarmActivityDao()
        val history = RoomMissionHistoryDataSource(db.missionHistoryDao())
        activity.record(AlarmActivityEntity.rang("alarm", "one", AlarmActivityTimestamp(1_000, "2026-09-28")))
        activity.record(AlarmActivityEntity.rang("alarm", "one:retry-1", AlarmActivityTimestamp(5_000, "2026-09-28")))
        history.record(MissionHistoryDto("FAILURE", "2026-09-28", "one", missionCompletedAtEpochMillis = 4_000))
        history.record(MissionHistoryDto("SUCCESS", "2026-09-28", "one:retry-1", missionCompletedAtEpochMillis = 7_000))
        db.missionHistoryDao().insert(com.joon.ringout.data.missionhistory.MissionHistoryEntity(result = "SUCCESS", completedAt = "2026-09-28"))
        val result = history.getHistory(MissionYearMonth(2026, 9))
        assertEquals(listOf(1_000L, 5_000L, null), result.map { it.ringingStartedAtEpochMillis })
        assertEquals(listOf(4_000L, 7_000L, null), result.map { it.missionCompletedAtEpochMillis })
        assertTrue(result.all { it.ringingStoppedAtEpochMillis == null })
        assertEquals(2, activity.observeCounts("2026-09-28").first().ringingCount)
    }

    @Test
    fun `결과 저장 후 늦게 저장된 울림 종료 시각도 기록 구독에 반영한다`() = withDatabase { db ->
        val history = RoomMissionHistoryDataSource(db.missionHistoryDao())
        history.record(MissionHistoryDto("SUCCESS", "2026-09-28", "one", missionCompletedAtEpochMillis = 3_000))
        val firstEmission = CompletableDeferred<Unit>()
        kotlinx.coroutines.coroutineScope {
            val received = async {
                withTimeout(5_000) {
                    history.observeHistory(MissionYearMonth(2026, 9))
                        .onEach { firstEmission.complete(Unit) }.take(2).toList()
                }
            }
            firstEmission.await()
            db.alarmActivityDao().mergeOccurrenceTimes(AlarmOccurrenceTimesEntity("one", ringingStoppedAtEpochMillis = 2_000))
            val emissions = received.await()
            assertNull(emissions.first().single().ringingStoppedAtEpochMillis)
            assertEquals(2_000L, emissions.last().single().ringingStoppedAtEpochMillis)
        }
    }

    private fun withDatabase(block: suspend (RingoutDatabase) -> Unit) = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
        try { block(database) } finally { database.close() }
    }
}
