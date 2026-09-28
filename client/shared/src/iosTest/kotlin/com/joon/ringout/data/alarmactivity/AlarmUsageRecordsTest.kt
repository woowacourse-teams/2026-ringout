package com.joon.ringout.data.alarmactivity

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.missionhistory.MissionHistoryDto
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.groupByAlarm
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmUsageRecordsTest {
    private val september = MissionYearMonth(2026, 9)

    @Test
    fun `미션 결과가 없어도 울린 날에 카드를 조회하고 종료 시각을 연결한다`() = withDatabase { db ->
        val activity = db.alarmActivityDao()
        val records = RoomMissionHistoryDataSource(db.missionHistoryDao())
        activity.record(AlarmActivityEntity.rang("alarm", "one", AlarmActivityTimestamp(1_000, "2026-09-28")))

        val ringing = records.getRecords(september).single()
        assertEquals("one", ringing.occurrenceId)
        assertEquals("alarm", ringing.alarmId)
        assertEquals(MissionDate.parse("2026-09-28"), ringing.date)
        assertEquals(1_000L, ringing.ringingStartedAtEpochMillis)
        assertNull(ringing.ringingStoppedAtEpochMillis)
        assertNull(ringing.result)
        assertNull(ringing.missionCompletedAtEpochMillis)
        assertTrue(records.getHistory(september).isEmpty())

        activity.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity("one", ringingStoppedAtEpochMillis = 2_000))
        val stopped = records.getRecords(september).single()
        assertEquals(ringing.key, stopped.key)
        assertEquals(2_000L, stopped.ringingStoppedAtEpochMillis)
        assertNull(stopped.result)
    }

    @Test
    fun `울림과 종료와 도착이 저장될 때 같은 카드 구독에 차례로 반영한다`() = withDatabase { db ->
        val activity = db.alarmActivityDao()
        val records = RoomMissionHistoryDataSource(db.missionHistoryDao())
        val emptySeen = CompletableDeferred<Unit>()
        val ringingSeen = CompletableDeferred<Unit>()
        val stoppedSeen = CompletableDeferred<Unit>()
        coroutineScope {
            val arrived = async {
                withTimeout(5_000) {
                    records.observeRecords(september).onEach { entries ->
                        if (entries.isEmpty()) emptySeen.complete(Unit)
                        entries.singleOrNull()?.let {
                            assertEquals("occurrence:one", it.key)
                            if (it.ringingStartedAtEpochMillis == 1_000L) ringingSeen.complete(Unit)
                            if (it.ringingStoppedAtEpochMillis == 2_000L) stoppedSeen.complete(Unit)
                        }
                    }.first { it.singleOrNull()?.result == MissionResult.SUCCESS }.single()
                }
            }
            emptySeen.await()
            activity.record(AlarmActivityEntity.rang("alarm", "one", AlarmActivityTimestamp(1_000, "2026-09-28")))
            ringingSeen.await()
            activity.mergeOccurrenceTimes(AlarmOccurrenceTimesEntity("one", ringingStoppedAtEpochMillis = 2_000))
            stoppedSeen.await()
            records.record(MissionHistoryDto("SUCCESS", "2026-09-28", "one", missionCompletedAtEpochMillis = 3_000))

            val result = arrived.await()
            assertEquals(1_000L, result.ringingStartedAtEpochMillis)
            assertEquals(2_000L, result.ringingStoppedAtEpochMillis)
            assertEquals(3_000L, result.missionCompletedAtEpochMillis)
            assertEquals(1, records.getRecords(september).size)
        }
    }

    @Test
    fun `다음 달에 도착해도 울린 날짜의 카드를 유지하고 완료일에 중복 생성하지 않는다`() = withDatabase { db ->
        val records = RoomMissionHistoryDataSource(db.missionHistoryDao())
        db.alarmActivityDao().record(AlarmActivityEntity.rang("alarm", "one", AlarmActivityTimestamp(1_000, "2026-09-30")))
        val original = records.getRecords(september).single()
        records.record(MissionHistoryDto("SUCCESS", "2026-10-01", "one", missionCompletedAtEpochMillis = 3_000))

        val result = records.getRecords(september).single()
        assertEquals(original.key, result.key)
        assertEquals(original.date, result.date)
        assertEquals(MissionResult.SUCCESS, result.result)
        assertTrue(records.getRecords(MissionYearMonth(2026, 10)).isEmpty())
        assertEquals(1, records.getHistory(MissionYearMonth(2026, 10)).size)
    }

    @Test
    fun `재울림과 식별자 없는 기존 실행 기록을 보존하고 울림 시각순으로 정렬한다`() = withDatabase { db ->
        val activity = db.alarmActivityDao()
        val dao = db.missionHistoryDao()
        val records = RoomMissionHistoryDataSource(dao)
        activity.record(AlarmActivityEntity.rang("alarm", "one:retry-1", AlarmActivityTimestamp(5_000, "2026-09-28")))
        activity.record(AlarmActivityEntity.rang("alarm", "one", AlarmActivityTimestamp(1_000, "2026-09-28")))
        records.record(MissionHistoryDto("FAILURE", "2026-09-28", "one", missionCompletedAtEpochMillis = 4_000))
        records.record(MissionHistoryDto("SUCCESS", "2026-09-28", "unobserved", missionCompletedAtEpochMillis = 8_000))
        repeat(2) { dao.insert(MissionHistoryEntity(result = "SUCCESS", completedAt = "2026-09-28")) }

        val result = records.getRecords(september)
        assertEquals(5, result.size)
        assertEquals(5, result.map { it.key }.distinct().size)
        assertEquals(listOf(null, null, "one", "one:retry-1", "unobserved"), result.map { it.occurrenceId })
        assertNull(result.single { it.occurrenceId == "one:retry-1" }.result)
        assertEquals(1_000L, result.single { it.occurrenceId == "one" }.ringingStartedAtEpochMillis)
        assertTrue(result.take(2).all { it.ringingStartedAtEpochMillis == null })
        val cards = result.groupByAlarm()
        assertEquals(4, cards.size)
        val alarmCard = cards.single { it.alarmId == "alarm" }
        assertEquals(listOf("one", "one:retry-1"), alarmCard.entries.map { it.occurrenceId })
        assertEquals(2, activity.observeCounts("2026-09-28").first().ringingCount)
    }

    private fun withDatabase(block: suspend (RingoutDatabase) -> Unit) = runBlocking<Unit> {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
        try { block(database) } finally { database.close() }
    }
}
