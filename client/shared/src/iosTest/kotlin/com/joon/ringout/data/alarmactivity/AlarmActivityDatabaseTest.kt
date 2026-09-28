package com.joon.ringout.data.alarmactivity

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmActivityDatabaseTest {
    @Test
    fun `울림은 발생한 날짜에 중복 없이 집계하고 알람을 삭제해도 유지한다`() = withDatabase { database ->
        val yesterday = AlarmActivityTimestamp(1_000, "2026-09-27")
        val today = AlarmActivityTimestamp(2_000, "2026-09-28")
        val alarms = RoomAlarmDataSource(database.alarmDao())
        val activity = database.alarmActivityDao()
        val alarm = savedAlarm()

        alarms.replace(alarm)
        alarms.replace(alarm.copy(request = alarm.request.copy(time = "08:00")))
        alarms.setEnabled(alarm.request.id, false)
        alarms.setEnabled(alarm.request.id, true)
        repeat(5) { attempt ->
            val event = AlarmActivityEntity.rang(alarm.request.id, "occurrence-$attempt", today)
            activity.record(event)
            activity.record(event)
        }
        alarms.delete(alarm.request.id)

        val yesterdayCounts = activity.observeCounts(yesterday.localDate).first()
        val todayCounts = activity.observeCounts(today.localDate).first()
        assertEquals(0, yesterdayCounts.ringingCount)
        assertEquals(5, todayCounts.ringingCount)
    }

    @Test
    fun `알람 생성과 수정과 이관은 활동 이벤트를 저장하지 않는다`() = withDatabase { database ->
        val now = AlarmActivityTimestamp(1_000, "2026-09-28")
        val alarms = RoomAlarmDataSource(database.alarmDao())
        val alarm = savedAlarm()
        alarms.importLegacyIfNeeded(listOf(alarm), "legacy", 1_000)
        alarms.replace(alarm)
        alarms.replace(alarm.copy(request = alarm.request.copy(time = "08:00")))
        alarms.migrateId(alarm.request.id, "new-id")

        val counts = database.alarmActivityDao().observeCounts(now.localDate).first()
        assertEquals(0, counts.ringingCount)
        assertNull(counts.trackingStartDate)
    }

    @Test
    fun `집계 이전 날짜는 미확인이고 집계 시작일과 이후 영건을 구분한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        dao.initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = 1_000, localDate = "2026-09-28"))
        val repository = RoomAlarmActivityRepository(dao)

        val previous = repository.observeSummary(MissionDate.parse("2026-09-27")).first()
        val firstDay = repository.observeSummary(MissionDate.parse("2026-09-28")).first()
        val later = repository.observeSummary(MissionDate.parse("2026-09-29")).first()

        assertNull(previous.ringingCount)
        assertEquals(0, firstDay.ringingCount)
        assertTrue(firstDay.isTrackingStartDate)
        assertEquals(0, later.ringingCount)
    }

    @Test
    fun `재시작 후 같은 울림 관찰과 자정 이후 관찰을 중복 집계하지 않는다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        val first = AlarmActivityEntity.rang("alarm", "observed-1", AlarmActivityTimestamp(1_000, "2026-09-28"))
        dao.recordObservedRinging(mapOf("system-alarm" to first))
        val next = AlarmActivityEntity.rang("alarm", "observed-2", AlarmActivityTimestamp(2_000, "2026-09-29"))
        // A new DAO caller represents a runtime restarted with no in-memory session.
        database.alarmActivityDao().recordObservedRinging(mapOf("system-alarm" to next))
        assertEquals(1, dao.observeCounts("2026-09-28").first().ringingCount)
        assertEquals(0, dao.observeCounts("2026-09-29").first().ringingCount)

        dao.recordObservedRinging(emptyMap())
        dao.recordObservedRinging(mapOf("system-alarm" to next))
        assertEquals(1, dao.observeCounts("2026-09-29").first().ringingCount)
    }

    @Test
    fun `동시에 관찰된 알람과 재울림은 각각 집계한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        val now = AlarmActivityTimestamp(1_000, "2026-09-28")
        dao.recordObservedRinging(mapOf(
            "system-1" to AlarmActivityEntity.rang("alarm-1", "one", now),
            "system-2" to AlarmActivityEntity.rang("alarm-2", "two", now),
        ))
        dao.recordObservedRinging(mapOf("retry-1" to AlarmActivityEntity.rang("alarm-1", "one:retry-1", now)))

        assertEquals(3, dao.observeCounts(now.localDate).first().ringingCount)
        assertTrue(RoomAlarmActivityRepository(dao, observedRingingOnly = true)
            .observeSummary(MissionDate.parse(now.localDate)).first().observedRingingOnly)
    }

    @Test
    fun `늦게 복구한 이벤트는 원래 날짜에 집계하고 집계 시작 시각도 복구한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        dao.initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = 2_000, localDate = "2026-09-29"))
        dao.record(AlarmActivityEntity.rang("alarm", "pending", AlarmActivityTimestamp(1_000, "2026-09-28")))
        val result = RoomAlarmActivityRepository(dao).observeSummary(MissionDate.parse("2026-09-28")).first()
        assertEquals(1, result.ringingCount)
        assertTrue(result.isTrackingStartDate)
    }

    @Test
    fun `시간대 변경으로 집계 시작일보다 이전 날짜에 저장된 울림도 표시한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        dao.initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = 1_000, localDate = "2026-09-29"))
        dao.record(AlarmActivityEntity.rang("alarm", "travel", AlarmActivityTimestamp(2_000, "2026-09-28")))
        val result = RoomAlarmActivityRepository(dao).observeSummary(MissionDate.parse("2026-09-28")).first()
        assertEquals(1, result.ringingCount)
        assertTrue(result.isTrackingStartDate)
    }

    private fun withDatabase(block: suspend (RingoutDatabase) -> Unit) = runBlocking {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>()
            .setDriver(BundledSQLiteDriver()).build()
        try { block(database) } finally { database.close() }
    }

    private fun savedAlarm() = SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = "alarm-1", time = "07:05", selectedDays = listOf("월", "금"),
            repeatEnabled = true, limitMinutes = 12, destinationName = "회사",
            destinationAddress = "서울", destinationLatitude = 37.5665, destinationLongitude = 126.978,
            targetDistanceKm = 1.2, alarmSoundName = "기본 알람음", alarmSoundUri = null,
        ),
        enabled = true,
    )
}
