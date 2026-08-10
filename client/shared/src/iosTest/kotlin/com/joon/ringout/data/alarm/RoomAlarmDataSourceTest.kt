package com.joon.ringout.data.alarm

import androidx.room3.Room
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomAlarmDataSourceTest {
    @Test
    fun replacesObservesTogglesAndDeletesAlarmSchedules() = runBlocking {
        withDatabase { database ->
            val dao = database.alarmDao()
            val dataSource = RoomAlarmDataSource(dao)
            val mondayAndFriday = savedAlarm(selectedDays = listOf("금", "월"))

            dataSource.replace(mondayAndFriday)

            assertEquals(
                mondayAndFriday.copy(
                    request = mondayAndFriday.request.copy(selectedDays = listOf("월", "금")),
                ),
                dataSource.observeAll().first().single(),
            )
            assertEquals(listOf("alarm-1"), dataSource.getEnabled().map { it.request.id })

            assertTrue(dataSource.setEnabled("alarm-1", false))
            assertFalse(assertNotNull(dataSource.getById("alarm-1")).enabled)
            assertTrue(dataSource.getEnabled().isEmpty())

            dataSource.replace(
                mondayAndFriday.copy(
                    request = mondayAndFriday.request.copy(selectedDays = listOf("일")),
                    enabled = false,
                ),
            )
            assertEquals(
                listOf("일"),
                assertNotNull(dataSource.getById("alarm-1")).request.selectedDays,
            )

            assertTrue(dataSource.delete("alarm-1"))
            assertFalse(dataSource.delete("alarm-1"))
            assertNull(dataSource.getById("alarm-1"))

            // Reinsert only the parent. Any repeat days here would mean cascade deletion failed.
            dao.upsertAlarm(mondayAndFriday.toAlarmWithRepeatDays().alarm)
            assertTrue(assertNotNull(dao.getById("alarm-1")).repeatDays.isEmpty())
        }
    }

    @Test
    fun importsLegacyAlarmsAndMarkerExactlyOnceInOneTransaction() = runBlocking {
        withDatabase { database ->
            val dataSource = RoomAlarmDataSource(database.alarmDao())
            val imported = savedAlarm()

            assertTrue(
                dataSource.importLegacyIfNeeded(
                    alarms = listOf(imported),
                    migrationId = LegacyMigrationId,
                    completedAtEpochMillis = 1_000L,
                ),
            )
            assertTrue(dataSource.hasStorageMigration(LegacyMigrationId))

            val ignoredReplacement = imported.copy(
                request = imported.request.copy(time = "22:30"),
            )
            assertFalse(
                dataSource.importLegacyIfNeeded(
                    alarms = listOf(ignoredReplacement),
                    migrationId = LegacyMigrationId,
                    completedAtEpochMillis = 2_000L,
                ),
            )
            assertEquals("07:05", assertNotNull(dataSource.getById("alarm-1")).request.time)
        }
    }

    private suspend fun withDatabase(
        block: suspend (RingoutDatabase) -> Unit,
    ) {
        val database = buildRingoutDatabase(Room.inMemoryDatabaseBuilder<RingoutDatabase>())
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun savedAlarm(
        selectedDays: List<String> = listOf("월", "금"),
    ) = SavedAlarmSchedule(
        request = AlarmScheduleRequest(
            id = "alarm-1",
            time = "07:05",
            selectedDays = selectedDays,
            repeatEnabled = true,
            limitMinutes = 12,
            destinationName = "회사",
            destinationAddress = "서울특별시 중구 세종대로 110",
            destinationLatitude = 37.5665,
            destinationLongitude = 126.978,
            targetDistanceKm = 1.2,
            alarmSoundName = "기본 알람음",
            alarmSoundUri = null,
        ),
        enabled = true,
    )

    private companion object {
        const val LegacyMigrationId = "android_alarm_shared_preferences_v1"
    }
}
