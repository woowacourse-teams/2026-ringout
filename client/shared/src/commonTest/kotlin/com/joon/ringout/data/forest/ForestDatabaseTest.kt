package com.joon.ringout.data.forest

import androidx.room3.Room
import androidx.sqlite.execSQL
import com.joon.ringout.data.arrival.RoomArrivalResultRepository
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.database.createMigrationTestDatabasePath
import com.joon.ringout.data.database.createMigrationTestSQLiteDriver
import com.joon.ringout.data.database.deleteMigrationTestDatabaseFiles
import com.joon.ringout.data.missionhistory.MissionHistoryDto
import com.joon.ringout.data.missionhistory.MissionHistoryEntity
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.domain.forest.ForestState
import com.joon.ringout.domain.forest.ForestTreeKind
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Shared contract executed against both Android SQLite and iOS bundled SQLite. */
class ForestDatabaseTestSuite {
    fun `숲을 열지 않아도 다섯째 날 자동으로 심고 여든한째 날에는 도착 기록만 늘어난다`() = runBlocking {
        withDatabase {
            val initial = forest.getForest()
            assertTrue(initial.trees.isEmpty())
            assertFalse(initial.isOnboardingCompleted)
            assertEquals(5, initial.growth.remainingDays)
            for (day in 1..81) {
                assertTrue(recordDay(day))
                if (day in listOf(4, 5, 6, 79, 80, 81)) {
                    // Check persisted rows before ever constructing a forest screen/view model.
                    val trees = database.forestDao().getForestTrees()
                    assertEquals(day.coerceAtMost(80) / 5, trees.size)
                    val state = forest.getForest()
                    assertEquals(day, state.growth.successfulDayCount)
                    assertEquals(day.coerceAtMost(80), state.growth.growthDayCount)
                    assertEquals(if (day >= 80) 0 else 5 - day % 5, state.growth.remainingDays)
                    assertEquals(day >= 80, state.growth.isComplete)
                }
            }
            val state = forest.getForest()
            assertEquals((1..16).toList(), state.trees.map { it.growthCycle })
            assertEquals((0..15).toSet(), state.trees.map { it.slotIndex }.toSet())
            assertTrue(state.trees.all { it.kind in ForestTreeKind.entries })
            assertEquals(16, state.pendingTrees.size)
            assertFalse(state.growth.hasGrowingSeed)
            assertEquals(81, arrivals.getGrowth().successfulDayCount)
            assertEquals(81, assertNotNull(arrivals.getResult("day-81")).successfulDayCountAtArrival)
            assertEquals(81, database.missionHistoryDao().getHistory("2026-01-01", "2026-12-31").size)
            val trees = state.trees
            reopen()
            assertEquals(trees, forest.getForest().trees)
        }
    }

    fun `다섯째 날 동시 도착과 같은 회차 재시도는 나무를 한 번만 심는다`() = runBlocking {
        withDatabase {
            (1..4).forEach { recordDay(it) }
            val results = coroutineScope {
                listOf("fifth", "fifth", "fifth-other").map { id ->
                    async(Dispatchers.Default) { recordDay(5, id) }
                }.awaitAll()
            }
            assertEquals(2, results.count { it })
            val tree = forest.getForest().trees.single()
            assertEquals(5_000L, tree.plantedAtEpochMillis)
            assertFalse(recordDay(5, "fifth"))
            assertTrue(recordDay(5, "same-date-later"))
            assertEquals(listOf(tree), forest.getForest().trees)
            assertEquals(5, arrivals.getGrowth().successfulDayCount)
            reopen()
            assertEquals(listOf(tree), forest.getForest().trees)
        }
    }

    fun `날짜 사이에 공백과 월 변경이 있어도 성장하고 이전 이력과 실패는 제외한다`() = runBlocking {
        withDatabase {
            database.missionHistoryDao().insert(MissionHistoryEntity(
                result = "SUCCESS", completedAt = "2026-01-01", occurrenceId = "legacy",
            ))
            recordDay(1, "before-activation", completedAt = 99)
            recordDay(2, "unknown-time", completedAt = null)
            recordDay(3, "failure", result = "FAILURE")
            assertEquals(0, forest.getForest().growth.successfulDayCount)
            assertTrue(forest.getForest().trees.isEmpty())
            for (day in listOf(5, 20, 40, 60, 75)) recordDay(day)
            assertEquals(1, forest.getForest().trees.size)
            assertEquals(5, forest.getForest().growth.successfulDayCount)
            assertEquals(0, forest.getForest().growth.filledSlots)
        }
    }

    fun `온보딩과 나무 안내 확인을 각각 저장하고 재시작과 재요청에서도 최초 확인을 유지한다`() = runBlocking {
        withDatabase {
            (1..10).forEach { recordDay(it) }
            val trees = forest.getForest().trees
            forest.completeOnboarding(11_000)
            forest.completeOnboarding(12_000)
            assertEquals(listOf(1, 2), forest.getForest().pendingTrees.map { it.growthCycle })
            forest.acknowledgeTree(2, 13_000)
            forest.acknowledgeTree(2, 14_000)
            forest.acknowledgeTree(3, 15_000) // A future tree must not be created or pre-acknowledged.
            reopen()
            val state = forest.getForest()
            assertEquals(11_000L, state.onboardingCompletedAtEpochMillis)
            assertEquals(listOf(1), state.pendingTrees.map { it.growthCycle })
            assertEquals(trees[0], state.trees[0])
            assertEquals(trees[1].copy(acknowledgedAtEpochMillis = 13_000), state.trees[1])
            assertNotNull(arrivals.observeLatestUnacknowledgedResult().first())
            arrivals.acknowledgeThrough("day-10", 16_000)
            assertEquals(listOf(1), forest.getForest().pendingTrees.map { it.growthCycle })
            (11..15).forEach { recordDay(it) }
            assertEquals(listOf(1, 3), forest.getForest().pendingTrees.map { it.growthCycle })
        }
    }

    fun `나무 저장 실패는 도착 결과와 미션 시각까지 롤백하고 재시도하면 한 번만 심는다`() = runBlocking {
        withDatabase {
            (1..4).forEach { recordDay(it) }
            executeSql("""
                CREATE TRIGGER fail_forest_insert BEFORE INSERT ON forest_trees
                BEGIN SELECT RAISE(ABORT, 'forest write failed'); END
            """.trimIndent())
            assertNotNull(runCatching { recordDay(5) }.exceptionOrNull())
            assertEquals(4, arrivals.getGrowth().successfulDayCount)
            assertNull(arrivals.getResult("day-5"))
            assertNull(database.alarmActivityDao().getOccurrenceTimes("day-5"))
            assertEquals(4, database.missionHistoryDao().getHistory("2026-01-01", "2026-12-31").size)
            assertTrue(forest.getForest().trees.isEmpty())
            executeSql("DROP TRIGGER fail_forest_insert")
            assertTrue(recordDay(5))
            assertFalse(recordDay(5))
            assertEquals(1, forest.getForest().trees.size)
            assertEquals(5, forest.getForest().growth.successfulDayCount)
        }
    }

    fun `안내 확인 저장 실패는 미확인 상태를 보존하고 재시도할 수 있다`() = runBlocking {
        withDatabase {
            (1..5).forEach { recordDay(it) }
            executeSql(
                "CREATE TRIGGER fail_forest_ack BEFORE UPDATE ON forest_trees BEGIN SELECT RAISE(ABORT, 'ack failed'); END",
                "CREATE TRIGGER fail_forest_onboarding BEFORE UPDATE ON forest_state BEGIN SELECT RAISE(ABORT, 'onboarding failed'); END",
            )
            assertNotNull(runCatching { forest.completeOnboarding(6_000) }.exceptionOrNull())
            assertNotNull(runCatching { forest.acknowledgeTree(1, 6_000) }.exceptionOrNull())
            assertFalse(forest.getForest().isOnboardingCompleted)
            assertEquals(1, forest.getForest().pendingTrees.size)
            executeSql("DROP TRIGGER fail_forest_ack", "DROP TRIGGER fail_forest_onboarding")
            forest.completeOnboarding(7_000)
            forest.acknowledgeTree(1, 7_000)
            assertTrue(forest.getForest().isOnboardingCompleted)
            assertTrue(forest.getForest().pendingTrees.isEmpty())
            assertEquals(1, forest.getForest().trees.size)
        }
    }

    fun `데이터베이스가 같은 성장 회차와 같은 칸의 중복 나무를 거부한다`() = runBlocking {
        withDatabase {
            (1..5).forEach { recordDay(it) }
            val tree = database.forestDao().getForestTrees().single()
            assertNotNull(runCatching { database.forestDao().insertForestTree(tree) }.exceptionOrNull())
            assertNotNull(runCatching {
                database.forestDao().insertForestTree(tree.copy(growthCycle = 2))
            }.exceptionOrNull())
            assertEquals(listOf(tree), database.forestDao().getForestTrees())
        }
    }

    fun `관찰자는 성장과 심은 나무가 일치하는 상태와 안내 확인 변경을 받는다`() = runBlocking {
        withDatabase {
            coroutineScope {
                val states = Channel<ForestState>(Channel.UNLIMITED)
                val job = launch { forest.observeForest().collect(states::send) }
                suspend fun awaitState(predicate: (ForestState) -> Boolean): ForestState = withTimeout(10_000) {
                    var state = states.receive()
                    while (true) {
                        assertEquals(state.growth.completedTreeCount, state.trees.size)
                        if (predicate(state)) return@withTimeout state
                        state = states.receive()
                    }
                    @Suppress("UNREACHABLE_CODE") error("unreachable")
                }
                try {
                    awaitState { it.growth.successfulDayCount == 0 }
                    (1..5).forEach { recordDay(it) }
                    assertEquals(1, awaitState { it.growth.successfulDayCount == 5 }.pendingTrees.size)
                    forest.completeOnboarding(6_000)
                    awaitState { it.isOnboardingCompleted }
                    forest.acknowledgeTree(1, 7_000)
                    assertEquals(1, awaitState { it.pendingTrees.isEmpty() }.trees.size)
                } finally {
                    job.cancelAndJoin()
                    states.close()
                }
            }
        }
    }

    fun `버전 십사 업데이트에서 기존 도착과 확인 상태를 보존하고 획득한 나무만 반영한다`() = runBlocking {
        withDatabase {
            (1..12).forEach { recordDay(it) }
            recordDay(5, "same-date")
            arrivals.acknowledgeThrough("day-5", 20_000)
            database.missionHistoryDao().insert(MissionHistoryEntity(
                result = "SUCCESS", completedAt = "2025-12-01", occurrenceId = "excluded-legacy",
            ))
            val original = (1..12).map { arrivals.getResult("day-$it") }
            downgradeToVersion14()
            // Even an unrelated first read must complete the migration and materialize the trees.
            assertEquals(12, arrivals.getGrowth().successfulDayCount)
            assertEquals(2, database.forestDao().getForestTrees().size)
            val state = forest.getForest()
            assertEquals(listOf(1, 2), state.trees.map { it.growthCycle })
            assertEquals(listOf(5_000L, 10_000L), state.trees.map { it.plantedAtEpochMillis })
            assertEquals(2, state.trees.map { it.slotIndex }.toSet().size)
            assertEquals(2, state.growth.filledSlots)
            assertEquals(3, state.growth.remainingDays)
            assertFalse(state.isOnboardingCompleted)
            assertEquals(state.trees, state.pendingTrees)
            assertEquals(original, (1..12).map { arrivals.getResult("day-$it") })
            assertEquals(100L, database.arrivalResultDao().getGrowthActivation())
            assertEquals(15L, databaseVersion())
            reopen()
            assertEquals(state, forest.getForest())
            (13..15).forEach { recordDay(it) }
            assertEquals(3, forest.getForest().trees.size)
            assertEquals(state.trees, forest.getForest().trees.take(2))
        }
    }

    fun `업데이트 전 성공일이 여든일을 넘어도 열여섯 그루까지만 소급한다`() = runBlocking {
        withDatabase {
            (1..85).forEach { recordDay(it) }
            downgradeToVersion14()
            val state = forest.getForest()
            assertEquals(85, state.growth.successfulDayCount)
            assertEquals(80, state.growth.growthDayCount)
            assertTrue(state.growth.isComplete)
            assertEquals(16, state.trees.size)
            assertEquals((0..15).toSet(), state.trees.map { it.slotIndex }.toSet())
            recordDay(86)
            reopen()
            assertEquals(state.trees, forest.getForest().trees)
            assertEquals(86, arrivals.getGrowth().successfulDayCount)
        }
    }

    fun `업데이트 전 성공일이 다섯일 미만이면 씨앗 진행도만 유지한다`() = runBlocking {
        for (days in listOf(0, 4)) withDatabase {
            (1..days).forEach { recordDay(it) }
            downgradeToVersion14()
            val state = forest.getForest()
            assertTrue(state.trees.isEmpty())
            assertEquals(days, state.growth.filledSlots)
            assertEquals(5 - days, state.growth.remainingDays)
            assertFalse(state.isOnboardingCompleted)
        }
    }

    private suspend fun withDatabase(block: suspend Fixture.() -> Unit) {
        val path = createMigrationTestDatabasePath()
        val fixture = Fixture(path, openDatabase(path))
        try {
            fixture.block()
        } finally {
            fixture.database.close()
            deleteMigrationTestDatabaseFiles(path)
        }
    }

    private class Fixture(private val path: String, var database: RingoutDatabase) {
        val forest get() = RoomForestRepository(database.forestDao())
        val arrivals get() = RoomArrivalResultRepository(database.arrivalResultDao())

        suspend fun recordDay(
            day: Int,
            id: String = "day-$day",
            completedAt: Long? = day * 1_000L,
            result: String = "SUCCESS",
        ): Boolean = RoomMissionHistoryDataSource(database.missionHistoryDao()).record(MissionHistoryDto(
            result = result,
            completedAt = MissionDate.of(2026, 1, 1).plusDays(day - 1).iso8601,
            occurrenceId = id,
            ringingStoppedAtEpochMillis = completedAt?.minus(1),
            missionCompletedAtEpochMillis = completedAt,
        ))

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

        suspend fun downgradeToVersion14() {
            forest.getForest() // Ensure a schema exists even for an empty installation.
            executeSql(
                "DROP TABLE forest_trees",
                "DROP TABLE forest_state",
                // Identity hash from committed schema 14.json; all other tables are unchanged.
                "UPDATE room_master_table SET identity_hash = 'd9d139b7d5784a0097c765a22affc38e' WHERE id = 42",
                "PRAGMA user_version = 14",
            )
        }

        fun databaseVersion(): Long = createMigrationTestSQLiteDriver().open(path).use { connection ->
            connection.prepare("PRAGMA user_version").use { statement ->
                assertTrue(statement.step())
                statement.getLong(0)
            }
        }
    }

    private companion object {
        fun openDatabase(path: String): RingoutDatabase = buildRingoutDatabase(
            builder = Room.databaseBuilder<RingoutDatabase>(name = path),
            driver = createMigrationTestSQLiteDriver(),
            arrivalGrowthActivatedAtEpochMillis = 100,
        )
    }
}
