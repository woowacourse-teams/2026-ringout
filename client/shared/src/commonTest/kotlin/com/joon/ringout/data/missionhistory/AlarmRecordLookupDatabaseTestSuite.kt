package com.joon.ringout.data.missionhistory

import androidx.room3.Room
import com.joon.ringout.data.alarmactivity.AlarmActivityEntity
import com.joon.ringout.data.alarmactivity.AlarmActivityTimestamp
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.database.createMigrationTestDatabasePath
import com.joon.ringout.data.database.createMigrationTestSQLiteDriver
import com.joon.ringout.data.database.deleteMigrationTestDatabaseFiles
import com.joon.ringout.domain.missionhistory.GetAlarmRecordGroup
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.groupByAlarm
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmRecordLookupDatabaseTestSuite {
    fun `자정을 넘어 다음 달에 성공한 회차는 울린 날짜의 같은 알람 버전 그룹을 찾는다`() = withDatabase { db ->
        val activity = db.alarmActivityDao()
        val history = RoomMissionHistoryDataSource(db.missionHistoryDao())
        val ringingDate = "2026-09-30"
        activity.record(AlarmActivityEntity.rang("alarm", "first", AlarmActivityTimestamp(1_000, ringingDate), 1))
        activity.record(AlarmActivityEntity.rang("alarm", "retry", AlarmActivityTimestamp(2_000, ringingDate), 1))
        activity.record(AlarmActivityEntity.rang("alarm", "edited", AlarmActivityTimestamp(3_000, ringingDate), 2))
        activity.record(AlarmActivityEntity.rang("other", "other", AlarmActivityTimestamp(4_000, ringingDate), 1))
        history.record(MissionHistoryDto("SUCCESS", "2026-10-01", "retry", missionCompletedAtEpochMillis = 5_000))

        val daoRows = db.missionHistoryDao().getRecordsOnOccurrenceDate("retry")
        assertEquals(setOf("first", "retry", "edited", "other"), daoRows.mapNotNull { it.occurrenceId }.toSet())
        assertTrue(daoRows.all { it.date == ringingDate })
        val records = history.getRecordsOnOccurrenceDate("retry")
        assertEquals(MissionResult.SUCCESS, records.single { it.occurrenceId == "retry" }.result)
        assertEquals(3, records.groupByAlarm().size)

        val group = assertNotNull(GetAlarmRecordGroup(DefaultMissionHistoryRepository(history)).byOccurrenceId("retry"))
        assertEquals(MissionDate.parse(ringingDate), group.date)
        assertEquals(listOf("first", "retry"), group.entries.map { it.occurrenceId })
        assertTrue(group.entries.all { it.scheduleVersion == 1L })
    }

    fun `울림 이벤트가 없는 성공 기록은 완료일에 조회하고 식별자가 없는 과거 기록도 그룹 키로 찾는다`() = withDatabase { db ->
        val dao = db.missionHistoryDao()
        val history = RoomMissionHistoryDataSource(dao)
        history.record(MissionHistoryDto("SUCCESS", "2026-10-01", "unobserved", missionCompletedAtEpochMillis = 5_000))
        dao.insert(MissionHistoryEntity(result = "SUCCESS", completedAt = "2026-10-01"))

        val records = history.getRecordsOnOccurrenceDate("unobserved")
        assertEquals(2, records.size)
        assertTrue(records.all { it.date == MissionDate.parse("2026-10-01") })
        assertEquals(MissionResult.SUCCESS, records.single { it.occurrenceId == "unobserved" }.result)
        val legacy = history.getRecords(MissionYearMonth(2026, 10)).single { it.occurrenceId == null }
        val expected = listOf(legacy).groupByAlarm().single()
        val lookup = GetAlarmRecordGroup(DefaultMissionHistoryRepository(history))
        assertEquals(expected, lookup.byDateAndKey(legacy.date, expected.key))
        assertNotNull(lookup.byOccurrenceId("unobserved"))
        assertNull(lookup.byOccurrenceId("missing"))
        assertNull(lookup.byOccurrenceId(" "))
        assertTrue(dao.getRecordsOnOccurrenceDate("missing").isEmpty())
        assertTrue(history.getRecordsOnOccurrenceDate("missing").isEmpty())
    }

    private fun withDatabase(block: suspend (RingoutDatabase) -> Unit) = runBlocking {
        val path = createMigrationTestDatabasePath()
        val database = buildRingoutDatabase(
            builder = Room.databaseBuilder<RingoutDatabase>(name = path),
            driver = createMigrationTestSQLiteDriver(),
            arrivalGrowthActivatedAtEpochMillis = 1_000,
        )
        try {
            block(database)
        } finally {
            database.close()
            deleteMigrationTestDatabaseFiles(path)
        }
    }
}
