@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.alarm

import androidx.room3.Room
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RoomAlarmDataSourceTest {
    @Test
    fun activeCollectorReceivesCreateToggleAndDeleteMutationsInOrder() = runBlocking {
        withDatabase { database ->
            val dataSource = RoomAlarmDataSource(database.alarmDao())
            val emissions = Channel<List<SavedAlarmSchedule>>(Channel.UNLIMITED)
            val collector = launch {
                dataSource.observeAll().collect(emissions::send)
            }

            assertTrue(emissions.receiveWithinTimeout().isEmpty())
            dataSource.replace(savedAlarm())
            assertEquals(
                listOf(true),
                emissions.receiveWithinTimeout().map(SavedAlarmSchedule::enabled),
            )
            assertTrue(dataSource.setEnabled("alarm-1", false))
            assertEquals(
                listOf(false),
                emissions.receiveWithinTimeout().map(SavedAlarmSchedule::enabled),
            )
            assertTrue(dataSource.setEnabled("alarm-1", true))
            assertEquals(
                listOf(true),
                emissions.receiveWithinTimeout().map(SavedAlarmSchedule::enabled),
            )
            assertTrue(dataSource.delete("alarm-1"))
            assertTrue(emissions.receiveWithinTimeout().isEmpty())

            collector.cancel()
            emissions.close()
        }
    }

    @Test
    fun replacingSameIdRemovesOldDaysAndCanonicalizesDuplicateDays() = runBlocking {
        withDatabase { database ->
            val dao = database.alarmDao()
            val dataSource = RoomAlarmDataSource(dao)
            dataSource.replace(savedAlarm(selectedDays = listOf("화", "목")))

            dataSource.replace(
                savedAlarm(selectedDays = listOf("월", "월", "금")).copy(
                    request = savedAlarm(listOf("월", "월", "금")).request.copy(
                        time = "20:45",
                        destinationName = "집",
                    ),
                    enabled = false,
                ),
            )

            val stored = dataSource.getAll().single()
            assertEquals("20:45", stored.request.time)
            assertEquals("집", stored.request.destinationName)
            assertEquals(listOf("월", "금"), stored.request.selectedDays)
            assertFalse(stored.enabled)
            assertEquals(2, assertNotNull(dao.getById("alarm-1")).repeatDays.size)
        }
    }

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

    @Test
    fun keepsOneShotRepeatDaysAndEnabledStatesAfterDatabaseReopen() = runBlocking {
        val databasePath = temporaryDatabasePath()
        try {
            buildRingoutDatabase(
                Room.databaseBuilder<RingoutDatabase>(name = databasePath),
            ).let { database ->
                try {
                    val dataSource = RoomAlarmDataSource(database.alarmDao())
                    dataSource.replace(savedAlarm(selectedDays = listOf("일", "월")))
                    dataSource.replace(
                        savedAlarm(selectedDays = emptyList()).copy(
                            request = savedAlarm(emptyList()).request.copy(
                                id = "alarm-2",
                                time = "22:30",
                                repeatEnabled = false,
                            ),
                            enabled = false,
                        ),
                    )
                } finally {
                    database.close()
                }
            }

            val reopenedDatabase = buildRingoutDatabase(
                Room.databaseBuilder<RingoutDatabase>(name = databasePath),
            )
            try {
                val restored = RoomAlarmDataSource(reopenedDatabase.alarmDao()).getAll()
                    .associateBy { it.request.id }
                assertEquals(setOf("alarm-1", "alarm-2"), restored.keys)
                assertEquals(listOf("월", "일"), restored.getValue("alarm-1").request.selectedDays)
                assertTrue(restored.getValue("alarm-1").enabled)
                assertTrue(restored.getValue("alarm-2").request.selectedDays.isEmpty())
                assertFalse(restored.getValue("alarm-2").enabled)
            } finally {
                reopenedDatabase.close()
            }
        } finally {
            deleteDatabaseFiles(databasePath)
        }
    }

    @Test
    fun migratesAlarmIdAtomicallyWithRepeatDaysAndState() = runBlocking {
        withDatabase { database ->
            val dataSource = RoomAlarmDataSource(database.alarmDao())
            val original = savedAlarm(selectedDays = listOf("월", "일")).copy(enabled = false)
            dataSource.replace(original)

            assertTrue(dataSource.migrateId("alarm-1", MigratedUuid))
            assertNull(dataSource.getById("alarm-1"))
            val migrated = assertNotNull(dataSource.getById(MigratedUuid))
            assertEquals(MigratedUuid, migrated.request.id)
            assertEquals(listOf("월", "일"), migrated.request.selectedDays)
            assertFalse(migrated.enabled)
        }
    }

    @Test
    fun idMigrationRejectsCollisionWithoutChangingEitherAlarm() = runBlocking {
        withDatabase { database ->
            val dataSource = RoomAlarmDataSource(database.alarmDao())
            dataSource.replace(savedAlarm())
            dataSource.replace(
                savedAlarm().copy(request = savedAlarm().request.copy(id = MigratedUuid)),
            )

            assertFailsWith<IllegalStateException> {
                dataSource.migrateId("alarm-1", MigratedUuid)
            }
            assertNotNull(dataSource.getById("alarm-1"))
            assertNotNull(dataSource.getById(MigratedUuid))
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

    private suspend fun Channel<List<SavedAlarmSchedule>>.receiveWithinTimeout() =
        withTimeout(FlowEmissionTimeoutMillis) { receive() }

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
        const val FlowEmissionTimeoutMillis = 1_000L
        const val MigratedUuid = "D08814F5-0B28-4454-91F0-F6931224EBD6"
    }

    private fun temporaryDatabasePath(): String =
        NSTemporaryDirectory() + "ringout-alarm-${NSUUID().UUIDString}.db"

    private fun deleteDatabaseFiles(databasePath: String) {
        listOf(databasePath, "$databasePath-shm", "$databasePath-wal").forEach { path ->
            if (NSFileManager.defaultManager.fileExistsAtPath(path)) {
                NSFileManager.defaultManager.removeItemAtPath(path, error = null)
            }
        }
    }
}
