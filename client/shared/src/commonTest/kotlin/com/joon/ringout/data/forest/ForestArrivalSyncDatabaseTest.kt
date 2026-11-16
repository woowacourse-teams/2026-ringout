package com.joon.ringout.data.forest

import androidx.room3.Room
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxEntity
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxKind
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceOutboxState
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncDao
import com.joon.ringout.data.alarmoccurrence.AlarmOccurrenceSyncEntity
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import com.joon.ringout.data.database.createMigrationTestDatabasePath
import com.joon.ringout.data.database.createMigrationTestSQLiteDriver
import com.joon.ringout.data.database.deleteMigrationTestDatabaseFiles
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
import kotlin.test.assertTrue

/** Runs the forest refresh signal contract against both platform SQLite drivers. */
class ForestArrivalSyncDatabaseTestSuite {
    fun `숲 갱신 횟수에는 현재 계정에서 전송 성공한 도착만 포함한다`() = runBlocking {
        withDao { dao ->
            val pending = dao.recordTerminal("pending", Owner)
            val otherAccount = dao.recordTerminal("other", "2")
            val forced = dao.recordTerminal("forced", Owner, AlarmOccurrenceOutboxKind.FORCE_ENDED)
            dao.completeStart(Owner, "pending", "11111111-1111-1111-1111-111111111111")
            assertTrue(dao.saveDeliveryState("2", otherAccount.id, AlarmOccurrenceOutboxState.SENT, 1))
            assertTrue(dao.saveDeliveryState(Owner, forced.id, AlarmOccurrenceOutboxState.SENT, 1))
            assertEquals(0, dao.count(Owner))
            assertEquals(1, dao.count("2"))

            assertTrue(dao.saveDeliveryState(Owner, pending.id, AlarmOccurrenceOutboxState.BLOCKED, 1,
                errorCode = "AUTH_REQUIRED"))
            assertEquals(0, dao.count(Owner))
            assertTrue(dao.saveDeliveryState(Owner, pending.id, AlarmOccurrenceOutboxState.PENDING, 2,
                nextAttemptAtEpochMillis = 9_000))
            assertEquals(0, dao.count(Owner))
            assertTrue(dao.saveDeliveryState(Owner, pending.id, AlarmOccurrenceOutboxState.SENT, 3))
            assertEquals(1, dao.count(Owner))
        }
    }

    fun `오래된 도착이 늦게 전송되어도 관찰 횟수가 늘고 중복 성공은 늘지 않는다`() = runBlocking {
        withDao { dao ->
            val older = dao.recordTerminal("older", Owner)
            val newer = dao.recordTerminal("newer", Owner)
            assertTrue(older.id < newer.id)
            coroutineScope {
                val counts = Channel<Int>(Channel.UNLIMITED)
                val observer = launch {
                    dao.observeSuccessfulArrivalSyncs(Owner).distinctUntilChanged().collect(counts::send)
                }
                suspend fun nextCount() = withTimeout(5_000) { counts.receive() }
                try {
                    assertEquals(0, nextCount())
                    assertTrue(dao.saveDeliveryState(Owner, newer.id, AlarmOccurrenceOutboxState.SENT, 1))
                    assertEquals(1, nextCount())
                    assertTrue(dao.saveDeliveryState(Owner, older.id, AlarmOccurrenceOutboxState.SENT, 2))
                    assertEquals(2, nextCount())
                    assertFalse(dao.saveDeliveryState(Owner, older.id, AlarmOccurrenceOutboxState.SENT, 3))
                    assertEquals(2, dao.count(Owner))
                } finally {
                    observer.cancelAndJoin()
                    counts.close()
                }
            }
        }
    }

    private suspend fun withDao(block: suspend (AlarmOccurrenceSyncDao) -> Unit) {
        val path = createMigrationTestDatabasePath()
        val database = buildRingoutDatabase(
            builder = Room.databaseBuilder<RingoutDatabase>(name = path),
            driver = createMigrationTestSQLiteDriver(),
        )
        try {
            block(database.alarmOccurrenceSyncDao())
        } finally {
            database.close()
            deleteMigrationTestDatabaseFiles(path)
        }
    }

    private suspend fun AlarmOccurrenceSyncDao.recordTerminal(
        executionId: String,
        owner: String,
        kind: AlarmOccurrenceOutboxKind = AlarmOccurrenceOutboxKind.ARRIVED,
    ): AlarmOccurrenceOutboxEntity {
        recordStart(AlarmOccurrenceSyncEntity(executionId, owner, "alarm-$executionId", 1, 900, 1_000))
        recordTerminal(owner, executionId, kind, 2_000, roomsEnabled = false)
        return getEvents(owner, executionId).single { it.kind == kind }
    }

    private suspend fun AlarmOccurrenceSyncDao.count(owner: String): Int =
        withTimeout(5_000) { observeSuccessfulArrivalSyncs(owner).first() }

    private companion object {
        const val Owner = "1"
    }
}
