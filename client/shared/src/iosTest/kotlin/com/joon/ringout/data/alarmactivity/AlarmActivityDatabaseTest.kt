package com.joon.ringout.data.alarmactivity

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.weekDates
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmActivityDatabaseTest {
    @Test
    fun `주 단위 조회는 날짜별 울림을 집계하고 기록 없는 날짜와 집계 이전 날짜를 구분한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        dao.initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = 1_000, localDate = "2026-09-28"))
        listOf("2026-09-28", "2026-09-28", "2026-09-30", "2026-10-04").forEachIndexed { index, date ->
            val event = AlarmActivityEntity.rang("alarm", "occurrence-$index", AlarmActivityTimestamp(2_000L + index, date))
            dao.record(event)
            dao.record(event)
        }
        val dates = MissionDate.parse("2026-09-28").weekDates()

        val summaries = RoomAlarmActivityRepository(dao, observedRingingOnly = true).observeSummaries(dates).first()

        assertEquals(dates.toSet(), summaries.keys)
        assertEquals(listOf(null, 2, 0, 1, 0, 0, 0), dates.map { summaries.getValue(it).ringingCount })
        assertTrue(summaries.getValue(MissionDate.parse("2026-09-28")).isTrackingStartDate)
        assertTrue(summaries.values.all { it.observedRingingOnly })
    }

    @Test
    fun `울림이 없는 주도 연도 경계를 넘어 날짜별 집계 상태를 반환한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        dao.initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = 1_000, localDate = "2025-12-30"))
        val dates = MissionDate.parse("2026-01-01").weekDates()

        val summaries = RoomAlarmActivityRepository(dao).observeSummaries(dates).first()

        assertEquals(dates.toSet(), summaries.keys)
        assertEquals(listOf(null, null, 0, 0, 0, 0, 0), dates.map { summaries.getValue(it).ringingCount })
        assertEquals(listOf(MissionDate.parse("2025-12-30")), summaries.filterValues { it.isTrackingStartDate }.keys.toList())
    }

    @Test
    fun `주 단위 구독 중 저장된 새 울림을 다시 조회하지 않고 전달한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        val date = MissionDate.parse("2026-09-28")
        dao.initializeTracking(AlarmActivityTrackingEntity(startedAtEpochMillis = 1_000, localDate = date.iso8601))
        val repository = RoomAlarmActivityRepository(dao)

        val updated = withTimeout(5_000) {
            repository.observeSummaries(date.weekDates()).first { summaries ->
                if (summaries.getValue(date).ringingCount == 0) {
                    dao.record(AlarmActivityEntity.rang("alarm", "new", AlarmActivityTimestamp(2_000, date.iso8601)))
                    false
                } else {
                    true
                }
            }
        }
        assertEquals(1, updated.getValue(date).ringingCount)
    }

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
        val repeated = AlarmActivityEntity.rang("alarm", "observed-1", AlarmActivityTimestamp(2_000, "2026-09-29"))
        // The native store restores the same occurrence ID after a runtime restart.
        database.alarmActivityDao().recordObservedRinging(mapOf("system-alarm" to repeated))
        assertEquals(1, dao.observeCounts("2026-09-28").first().ringingCount)
        assertEquals(0, dao.observeCounts("2026-09-29").first().ringingCount)

        dao.recordObservedRinging(emptyMap())
        val next = AlarmActivityEntity.rang("alarm", "observed-2", AlarmActivityTimestamp(2_000, "2026-09-29"))
        dao.recordObservedRinging(mapOf("system-alarm" to next))
        assertEquals(1, dao.observeCounts("2026-09-29").first().ringingCount)
    }

    @Test
    fun `중간 스냅샷을 놓쳐도 같은 시스템 알람의 새 실행을 별도로 보존한다`() = withDatabase { database ->
        val dao = database.alarmActivityDao()
        dao.recordObservedRinging(mapOf("system-alarm" to AlarmActivityEntity.rang(
            "alarm", "observed-1", AlarmActivityTimestamp(1_000, "2026-09-28"),
        )))
        dao.recordObservedRinging(mapOf("system-alarm" to AlarmActivityEntity.rang(
            "alarm", "observed-2", AlarmActivityTimestamp(2_000, "2026-09-29"),
        )))

        assertEquals(1, dao.observeCounts("2026-09-28").first().ringingCount)
        assertEquals(1, dao.observeCounts("2026-09-29").first().ringingCount)
        assertEquals(1_000L, dao.getOccurrenceTimes("observed-1")?.ringingStartedAtEpochMillis)
        assertEquals(2_000L, dao.getOccurrenceTimes("observed-2")?.ringingStartedAtEpochMillis)
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
