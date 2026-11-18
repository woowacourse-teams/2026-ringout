package com.joon.ringout.data.arrival

import androidx.room3.Room
import androidx.sqlite.execSQL
import com.joon.ringout.data.alarmactivity.AlarmOccurrenceTimesEntity
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.database.createMigrationTestDatabasePath
import com.joon.ringout.data.database.createMigrationTestSQLiteDriver
import com.joon.ringout.data.database.deleteMigrationTestDatabaseFiles
import com.joon.ringout.data.missionhistory.MissionHistoryDto
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Runs the same persistence contract against Android SQLite and the iOS bundled SQLite driver. */
class ArrivalResultDatabaseTestSuite {
    fun `첫 데이터베이스 접근이 성공 저장이어도 생성 시점 이후의 도착을 포함한다`() = runBlocking {
        val path = createMigrationTestDatabasePath()
        val database = buildRingoutDatabase(
            builder = Room.databaseBuilder<RingoutDatabase>(name = path),
            driver = createMigrationTestSQLiteDriver(),
            arrivalGrowthActivatedAtEpochMillis = 1_000,
        )
        try {
            // The first database operation must be the successful write, not a warm-up query.
            val history = RoomMissionHistoryDataSource(database.missionHistoryDao())
            assertTrue(
                history.record(
                    MissionHistoryDto(
                        result = "SUCCESS",
                        completedAt = "2026-11-09",
                        occurrenceId = "first-write",
                        missionCompletedAtEpochMillis = 1_001,
                    ),
                ),
            )

            val repository = RoomArrivalResultRepository(database.arrivalResultDao())
            assertEquals(1, assertNotNull(repository.getResult("first-write")).successfulDayCountAtArrival)
            assertEquals(1_000L, database.arrivalResultDao().getGrowthActivation())

            assertTrue(
                history.record(
                    MissionHistoryDto(
                        result = "SUCCESS",
                        completedAt = "2026-11-08",
                        occurrenceId = "recovered-before-activation",
                        missionCompletedAtEpochMillis = 999,
                    ),
                ),
            )
            assertNull(repository.getResult("recovered-before-activation"))
            assertEquals(1, repository.getGrowth().successfulDayCount)
        } finally {
            database.close()
            deleteMigrationTestDatabaseFiles(path)
        }
    }


    fun `같은 날 두 번 도착해도 성공일과 씨앗은 한 번만 증가한다`() = runBlocking {
        withDatabase {
            assertEquals(0, repository.getGrowth().successfulDayCount)
            assertTrue(record("first", "2026-11-09", 2_000))
            assertTrue(record("second", "2026-11-09", 3_000))

            val first = assertNotNull(repository.getResult("first"))
            val second = assertNotNull(repository.getResult("second"))
            assertEquals(1, first.successfulDayCountAtArrival)
            assertEquals(1, second.successfulDayCountAtArrival)
            assertTrue(first.grewOnThisArrival)
            assertFalse(second.grewOnThisArrival)
            assertEquals(1, repository.getGrowth().filledSlots)
        }
    }

    fun `날짜가 연속되지 않고 월이 바뀌어도 다섯째 날 나무가 되고 여섯째 날 새 씨앗을 채운다`() = runBlocking {
        withDatabase {
            val dates = listOf(
                "2026-10-31", "2026-11-01", "2026-11-03",
                "2026-11-10", "2026-11-20", "2026-12-01",
            )
            dates.forEachIndexed { index, date ->
                record("day-${index + 1}", date, 2_000L + index * 1_000)
            }

            val fifth = assertNotNull(repository.getResult("day-5"))
            assertEquals(5, fifth.successfulDayCountAtArrival)
            assertEquals(5, fifth.growth.filledSlots)
            assertEquals(0, fifth.growth.remainingDays)
            assertTrue(fifth.growth.isTreeComplete)
            val sixth = assertNotNull(repository.getResult("day-6"))
            assertEquals(6, sixth.successfulDayCountAtArrival)
            assertEquals(1, sixth.growth.filledSlots)
            assertEquals(1, sixth.growth.completedTreeCount)
            assertFalse(sixth.growth.isTreeComplete)
            assertEquals(6, repository.getGrowth().successfulDayCount)
        }
    }

    fun `새로운 성공이 늘어도 이전 도착의 날짜와 순번 스냅샷은 바뀌지 않는다`() = runBlocking {
        withDatabase {
            record("first", "2026-11-09", 2_000)
            val original = assertNotNull(repository.getResult("first"))
            record("next", "2026-11-12", 3_000)
            assertFalse(record("first", "2026-12-01", 9_000))

            assertEquals(original, repository.getResult("first"))
            assertEquals(MissionDate.parse("2026-11-09"), original.completedLocalDate)
            assertEquals(2, repository.getGrowth().successfulDayCount)
        }
    }

    fun `동시에 같은 날짜와 같은 회차를 기록해도 성장과 도착 결과가 중복되지 않는다`() = runBlocking {
        withDatabase {
            val saved = coroutineScope {
                listOf("first", "first", "second", "third").map { occurrenceId ->
                    async(Dispatchers.Default) {
                        record(occurrenceId, "2026-11-09", 2_000)
                    }
                }.awaitAll()
            }

            assertEquals(3, saved.count { it })
            val results = listOf("first", "second", "third").map { id ->
                assertNotNull(repository.getResult(id))
            }
            assertEquals(1, results.count(ArrivalResult::grewOnThisArrival))
            assertTrue(results.all { it.successfulDayCountAtArrival == 1 })
            assertEquals(1, repository.getGrowth().successfulDayCount)
            assertEquals(3, database.missionHistoryDao().getHistory("2026-11-01", "2026-11-30").size)
        }
    }

    fun `활성화 이전 완료와 시각 없는 성공 및 실패는 새 성장에 포함하지 않는다`() = runBlocking {
        withDatabase {
            record("old", "2026-11-08", 999)
            record("unknown-time", "2026-11-09", null)
            record("failed", "2026-11-09", 2_000, result = "FAILURE")
            assertNull(repository.getResult("old"))
            assertNull(repository.getResult("unknown-time"))
            assertNull(repository.getResult("failed"))
            assertEquals(0, repository.getGrowth().successfulDayCount)

            record("boundary", "2026-11-09", 1_000)
            assertEquals(1, assertNotNull(repository.getResult("boundary")).successfulDayCountAtArrival)
            assertEquals(4, database.missionHistoryDao().getHistory("2026-11-01", "2026-11-30").size)
        }
    }

    fun `기존 성공 기록과 늦게 재처리된 과거 회차를 성장에 소급 반영하지 않는다`() = runBlocking {
        withDatabase {
            database.missionHistoryDao().insert(
                MissionHistoryEntity(
                    result = "SUCCESS",
                    completedAt = "2026-11-01",
                    occurrenceId = "legacy",
                ),
            )
            assertFalse(record("legacy", "2026-11-01", 2_000))
            record("old-terminal", "2026-11-02", 900)
            assertFalse(record("old-terminal", "2026-11-02", 3_000))
            record("new", "2026-11-09", 4_000)

            assertNull(repository.getResult("legacy"))
            assertNull(repository.getResult("old-terminal"))
            assertEquals(1, assertNotNull(repository.getResult("new")).successfulDayCountAtArrival)
            assertEquals(1, repository.getGrowth().successfulDayCount)
        }
    }

    fun `먼저 기록한 실패를 같은 회차의 성공 재처리로 바꾸지 않는다`() = runBlocking {
        withDatabase {
            assertTrue(record("failed-first", "2026-11-09", 2_000, result = "FAILURE"))
            assertFalse(record("failed-first", "2026-11-09", 3_000))
            assertNull(repository.getResult("failed-first"))
            assertEquals(0, repository.getGrowth().successfulDayCount)
            assertEquals("FAILURE", database.missionHistoryDao().getHistory("2026-11-01", "2026-11-30").single().result)
        }
    }

    fun `결과를 확인하고 저장소를 다시 열어도 성장 기록과 최초 확인 시각이 유지된다`() = runBlocking {
        withDatabase {
            record("first", "2026-11-09", 2_000)
            record("second", "2026-11-10", 3_000)
            record("third", "2026-11-11", 4_000)
            repository.acknowledgeThrough("missing", 4_500)
            assertNull(assertNotNull(repository.getResult("first")).acknowledgedAtEpochMillis)
            repository.acknowledgeThrough("second", 5_000)
            repository.acknowledgeThrough("second", 6_000)
            reopen()

            assertEquals(5_000L, assertNotNull(repository.getResult("first")).acknowledgedAtEpochMillis)
            assertEquals(5_000L, assertNotNull(repository.getResult("second")).acknowledgedAtEpochMillis)
            assertNull(assertNotNull(repository.getResult("third")).acknowledgedAtEpochMillis)
            assertEquals("third", repository.observeLatestUnacknowledgedResult().first()?.occurrenceId)
            assertEquals(3, repository.getGrowth().successfulDayCount)

            repository.acknowledgeThrough("third", 7_000)
            assertNull(repository.observeLatestUnacknowledgedResult().first())
            assertFalse(record("third", "2026-11-11", 4_000))
            assertNull(repository.observeLatestUnacknowledgedResult().first())
            assertEquals(3, repository.getGrowth().successfulDayCount)
        }
    }

    fun `늦게 저장된 도착보다 실제 완료 시각이 최신인 미확인 결과를 선택한다`() = runBlocking {
        withDatabase {
            record("latest", "2026-11-11", 4_000)
            record("recovered-earlier", "2026-11-10", 3_000)
            assertEquals("latest", repository.observeLatestUnacknowledgedResult().first()?.occurrenceId)

            repository.acknowledgeThrough("recovered-earlier", 5_000)
            assertNull(assertNotNull(repository.getResult("latest")).acknowledgedAtEpochMillis)
            assertEquals("latest", repository.observeLatestUnacknowledgedResult().first()?.occurrenceId)
        }
    }

    fun `나중에 받은 실제 해제 시각은 빈 값만 보강하고 도착 스냅샷은 유지한다`() = runBlocking {
        withDatabase {
            record("arrival", "2026-11-09", 2_000)
            val original = assertNotNull(repository.getResult("arrival"))
            assertNull(original.alarmDismissedAtEpochMillis)
            database.alarmActivityDao().mergeOccurrenceTimes(
                AlarmOccurrenceTimesEntity(
                    occurrenceId = "arrival",
                    ringingStoppedAtEpochMillis = 1_500,
                ),
            )
            database.alarmActivityDao().mergeOccurrenceTimes(
                AlarmOccurrenceTimesEntity(
                    occurrenceId = "arrival",
                    ringingStoppedAtEpochMillis = 1_700,
                ),
            )
            assertEquals(original.copy(alarmDismissedAtEpochMillis = 1_500), repository.getResult("arrival"))

            record("known-time", "2026-11-10", 3_000, stoppedAt = 2_500)
            assertEquals(2_500L, assertNotNull(repository.getResult("known-time")).alarmDismissedAtEpochMillis)
        }
    }

    fun `도착 결과 저장이 실패하면 성공 기록과 시각도 롤백되고 재시도로 한 번만 성장한다`() = runBlocking {
        withDatabase {
            executeSql(
                """
                CREATE TRIGGER fail_arrival_insert BEFORE INSERT ON arrival_results
                BEGIN SELECT RAISE(ABORT, 'arrival write failed'); END
                """.trimIndent(),
            )
            val failure = runCatching { record("retry", "2026-11-09", 2_000, stoppedAt = 1_500) }.exceptionOrNull()
            assertNotNull(failure)
            assertTrue(database.missionHistoryDao().getHistory("2026-11-01", "2026-11-30").isEmpty())
            assertNull(database.alarmActivityDao().getOccurrenceTimes("retry"))
            assertNull(repository.getResult("retry"))
            assertEquals(0, repository.getGrowth().successfulDayCount)

            executeSql("DROP TRIGGER fail_arrival_insert")
            assertTrue(record("retry", "2026-11-09", 2_000, stoppedAt = 1_500))
            assertFalse(record("retry", "2026-11-09", 2_000, stoppedAt = 1_500))
            assertEquals(1, repository.getGrowth().successfulDayCount)
            assertEquals(1_500L, assertNotNull(repository.getResult("retry")).alarmDismissedAtEpochMillis)
        }
    }

    fun `도착과 확인을 관찰자에게 전달하고 성장 관찰은 확인 후에도 유지한다`() = runBlocking {
        withDatabase {
            coroutineScope {
                val arrivals = Channel<ArrivalResult?>(Channel.UNLIMITED)
                val growthCounts = Channel<Int>(Channel.UNLIMITED)
                val arrivalJob = launch {
                    repository.observeLatestUnacknowledgedResult().distinctUntilChanged().collect(arrivals::send)
                }
                val growthJob = launch {
                    repository.observeGrowth().distinctUntilChanged().collect { growth ->
                        growthCounts.send(growth.successfulDayCount)
                    }
                }
                try {
                    assertNull(withTimeout(5_000) { arrivals.receive() })
                    assertEquals(0, withTimeout(5_000) { growthCounts.receive() })
                    record("observed", "2026-11-09", 2_000)
                    assertEquals("observed", withTimeout(5_000) { arrivals.receive() }?.occurrenceId)
                    assertEquals(1, withTimeout(5_000) { growthCounts.receive() })
                    repository.acknowledgeThrough("observed", 3_000)
                    assertNull(withTimeout(5_000) { arrivals.receive() })
                    assertEquals(1, repository.observeGrowth().first().successfulDayCount)
                } finally {
                    arrivalJob.cancelAndJoin()
                    growthJob.cancelAndJoin()
                    arrivals.close()
                    growthCounts.close()
                }
            }
        }
    }

    private suspend fun withDatabase(block: suspend Fixture.() -> Unit) {
        val path = createMigrationTestDatabasePath()
        val fixture = Fixture(path, openDatabase(path))
        try {
            // Force schema creation before fixing the activation boundary.
            fixture.repository.getGrowth()
            fixture.executeSql("UPDATE arrival_growth_activation SET activated_at_epoch_millis = 1000 WHERE id = 1")
            fixture.block()
        } finally {
            fixture.database.close()
            deleteMigrationTestDatabaseFiles(path)
        }
    }

    private class Fixture(
        private val path: String,
        var database: RingoutDatabase,
    ) {
        val repository: RoomArrivalResultRepository
            get() = RoomArrivalResultRepository(database.arrivalResultDao())

        suspend fun record(
            occurrenceId: String,
            localDate: String,
            completedAt: Long?,
            result: String = "SUCCESS",
            stoppedAt: Long? = null,
        ): Boolean = RoomMissionHistoryDataSource(database.missionHistoryDao()).record(
            MissionHistoryDto(
                result = result,
                completedAt = localDate,
                occurrenceId = occurrenceId,
                ringingStoppedAtEpochMillis = stoppedAt,
                missionCompletedAtEpochMillis = completedAt,
            ),
        )

        fun reopen() {
            database.close()
            database = openDatabase(path)
        }

        fun executeSql(vararg statements: String) {
            database.close()
            createMigrationTestSQLiteDriver().open(path).use { connection ->
                statements.forEach(connection::execSQL)
            }
            database = openDatabase(path)
        }
    }

    private companion object {
        fun openDatabase(path: String): RingoutDatabase =
            buildRingoutDatabase(
                builder = Room.databaseBuilder<RingoutDatabase>(name = path),
                driver = createMigrationTestSQLiteDriver(),
            )
    }
}

