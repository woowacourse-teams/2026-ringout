@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.buildRingoutDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmOccurrenceSyncDatabaseTest {
    @Test
    fun `중복 해제와 종료 경합은 이동 시작 한 건과 최초 종료 결과만 보존한다`() = withDatabase { dao ->
        dao.recordStart(execution())
        dao.recordDismissal(Owner, Root, 2_000)
        dao.recordDismissal(Owner, Root, 2_100)
        dao.recordTerminal(Owner, Root, AlarmOccurrenceOutboxKind.FORCE_ENDED, 3_000)
        dao.recordTerminal(Owner, Root, AlarmOccurrenceOutboxKind.ARRIVED, 3_100)
        val events = dao.getUnsentMovements(Owner)
        assertEquals(listOf(com.joon.ringout.domain.alarmmovement.AlarmMovementAction.START_MOVEMENT,
            com.joon.ringout.domain.alarmmovement.AlarmMovementAction.GIVE_UP), events.map { it.action })
        assertEquals(listOf(2_000L, 3_000L), events.map { it.occurredAtEpochMillis })
        assertTrue(dao.getUnsentMovements("other").isEmpty())
        assertEquals(0, dao.saveMovementDelivery("other", events.first().id, AlarmOccurrenceOutboxState.SENT, 1))
    }

    @Test
    fun `DB 재실행 후에도 모임별 전송 성공과 재시도 상태를 유지한다`() = runBlocking {
        val path = NSTemporaryDirectory() + "ringout-movement-${NSUUID().UUIDString}.db"
        var database = openDatabase(path)
        try {
            var dao = database.alarmOccurrenceSyncDao()
            dao.recordStart(execution())
            dao.recordDismissal(Owner, Root, 2_000)
            val parent = dao.getUnsentMovements(Owner).single()
            dao.completeMovementTargets(Owner, parent.id, listOf(7, 8, 7))
            val children = dao.getUnsentMovements(Owner)
            assertEquals(listOf(7L, 8L), children.map { it.roomId })
            dao.saveMovementDelivery(Owner, children[0].id, AlarmOccurrenceOutboxState.SENT, 1)
            dao.saveMovementDelivery(Owner, children[1].id, AlarmOccurrenceOutboxState.PENDING, 2, 9_000, "MOVEMENT500")
            database.close()
            database = openDatabase(path)
            dao = database.alarmOccurrenceSyncDao()
            val restored = dao.getUnsentMovements(Owner).single()
            assertEquals(8L, restored.roomId)
            assertEquals(9_000L, restored.nextAttemptAtEpochMillis)
            assertEquals(2, restored.attemptCount)
            assertEquals("MOVEMENT500", restored.lastErrorCode)
            dao.completeMovementTargets(Owner, parent.id, listOf(7, 8))
            assertEquals(3, dao.getMovements(Owner, Root).size)
            assertEquals(1, dao.getUnsentMovements(Owner).size)
        } finally {
            database.close()
            listOf(path, "$path-wal", "$path-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        }
    }

    @Test
    fun `중복 이벤트는 원래 시각과 재울림 식별자를 보존하고 실행당 종료 결과는 하나만 남긴다`() = withDatabase { dao ->
        dao.recordStart(execution())
        dao.recordStart(execution().copy(startedAtEpochMillis = 99_000))
        dao.recordDismissal(Owner, Root, 2_000)
        dao.recordDismissal(Owner, Root, 99_000)
        dao.recordRepeat(Owner, Root, "retry-1", "event-1", 3_000)
        val repeated = dao.recordRepeat(Owner, Root, "retry-1", "new-event", 99_000)
        assertEquals("event-1", repeated.eventId)
        assertEquals(3_000L, repeated.ringingAtEpochMillis)
        dao.recordDismissal(Owner, "retry-1", 4_000)
        dao.recordTerminal(Owner, "retry-1", AlarmOccurrenceOutboxKind.ARRIVED, 5_000)
        dao.recordTerminal(Owner, "retry-1", AlarmOccurrenceOutboxKind.FORCE_ENDED, 99_000)

        val events = dao.getEvents(Owner, Root)
        assertEquals(listOf(AlarmOccurrenceOutboxKind.START, AlarmOccurrenceOutboxKind.INITIAL_DISMISSED,
            AlarmOccurrenceOutboxKind.REPEAT_RANG, AlarmOccurrenceOutboxKind.REPEAT_DISMISSED, AlarmOccurrenceOutboxKind.ARRIVED), events.map { it.kind })
        assertEquals(listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L), events.map { it.occurredAtEpochMillis })
        assertEquals(Root, dao.getExecutionForRinging(Owner, "retry-1")?.localExecutionId)
        assertEquals(1_000L, dao.getExecution(Owner, Root)?.startedAtEpochMillis)
    }

    @Test
    fun `계정이 다르면 실행과 대기 이벤트를 조회하거나 변경할 수 없다`() = withDatabase { dao ->
        dao.recordStart(execution())
        val id = dao.getEvents(Owner, Root).single().id
        assertNull(dao.getExecution("other", Root))
        assertNull(dao.getRinging("other", Root))
        assertNull(dao.getExecutionForRinging("other", Root))
        assertTrue(dao.getEvents("other", Root).isEmpty())
        assertTrue(dao.getUnsentEvents("other").isEmpty())
        assertFalse(dao.saveDeliveryState("other", id, AlarmOccurrenceOutboxState.BLOCKED, 1))
        assertFailsWith<IllegalStateException> { dao.recordStart(execution().copy(ownerAccountId = "other")) }
        assertFailsWith<IllegalStateException> { dao.recordDismissal("other", Root, 2_000) }
        assertFailsWith<IllegalStateException> { dao.completeStart("other", Root, ServerId) }
        assertEquals(Owner, dao.getExecution(Owner, Root)?.ownerAccountId)
        assertEquals(AlarmOccurrenceOutboxState.PENDING, dao.getEvents(Owner, Root).single().state)
    }

    @Test
    fun `새 실행의 울림 연결이 기존 재울림과 충돌하면 실행 저장도 롤백한다`() = withDatabase { dao ->
        dao.recordStart(execution())
        dao.recordRepeat(Owner, Root, "next-root", "event-1", 2_000)
        assertFailsWith<IllegalStateException> { dao.recordStart(execution().copy(localExecutionId = "next-root")) }
        assertNull(dao.getExecution(Owner, "next-root"))
        assertEquals(Root, dao.getRinging(Owner, "next-root")?.localExecutionId)
        assertEquals(2, dao.getEvents(Owner, Root).size)
    }

    @Test
    fun `같은 재울림 ID를 다른 실행에 연결하려 하면 기존 연결과 이벤트를 보존한다`() = withDatabase { dao ->
        dao.recordStart(execution())
        dao.recordStart(execution().copy(localExecutionId = "next-root", scheduledAtEpochMillis = 86_400_000))
        dao.recordRepeat(Owner, Root, "retry-1", "event-1", 2_000)
        assertFailsWith<IllegalStateException> { dao.recordRepeat(Owner, "next-root", "retry-1", "event-2", 3_000) }
        assertEquals(1, dao.getEvents(Owner, "next-root").size)
        assertEquals(Root, dao.getExecutionForRinging(Owner, "retry-1")?.localExecutionId)
    }

    @Test
    fun `POST 완료는 서버 ID와 완료 상태를 함께 저장하고 ID 변경을 거부한다`() = withDatabase { dao ->
        dao.recordStart(execution())
        val start = dao.getEvents(Owner, Root).single()
        assertFalse(dao.saveDeliveryState(Owner, start.id, AlarmOccurrenceOutboxState.SENT, 1))
        dao.completeStart(Owner, Root, ServerId)
        dao.completeStart(Owner, Root, ServerId)
        assertEquals(ServerId, dao.getExecution(Owner, Root)?.serverOccurrenceId)
        assertTrue(dao.getUnsentEvents(Owner).isEmpty())
        assertFailsWith<IllegalStateException> { dao.completeStart(Owner, Root, "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa") }
        assertEquals(ServerId, dao.getExecution(Owner, Root)?.serverOccurrenceId)
        assertFalse(dao.saveDeliveryState(Owner, start.id, AlarmOccurrenceOutboxState.PENDING, 2))
    }

    @Test
    fun `동시에 같은 이벤트를 저장해도 대기열에 한 건만 남긴다`() = withDatabase { dao ->
        dao.recordStart(execution())
        coroutineScope {
            (1..10).map { async { dao.recordRepeat(Owner, Root, "retry-1", "event-$it", it.toLong()) } }.awaitAll()
        }
        assertEquals(2, dao.getEvents(Owner, Root).size)
        assertNotNull(dao.getRinging(Owner, "retry-1"))
    }

    @Test
    fun `DB를 다시 열어도 계정별 실행과 재울림 및 재시도 보류 완료 상태를 복원한다`() = runBlocking {
        val path = NSTemporaryDirectory() + "ringout-outbox-${NSUUID().UUIDString}.db"
        var database = openDatabase(path)
        try {
            var dao = database.alarmOccurrenceSyncDao()
            dao.recordStart(execution())
            dao.completeStart(Owner, Root, ServerId)
            dao.recordRepeat(Owner, Root, "retry-1", "event-1", 3_000)
            dao.recordDismissal(Owner, "retry-1", 4_000)
            val events = dao.getEvents(Owner, Root)
            dao.saveDeliveryState(Owner, events[1].id, AlarmOccurrenceOutboxState.PENDING, 3, 9_000, "HTTP500", "재전송 대기")
            dao.saveDeliveryState(Owner, events[2].id, AlarmOccurrenceOutboxState.BLOCKED, 1, errorCode = "HTTP403", errorMessage = "권한 확인")
            dao.recordStart(execution().copy(localExecutionId = "other-root", ownerAccountId = "other"))
            database.close()
            database = openDatabase(path)
            dao = database.alarmOccurrenceSyncDao()

            assertEquals(ServerId, dao.getExecution(Owner, Root)?.serverOccurrenceId)
            assertEquals("event-1", dao.getRinging(Owner, "retry-1")?.eventId)
            val restored = dao.getUnsentEvents(Owner)
            assertEquals(listOf(events[1].id, events[2].id), restored.map { it.id })
            assertEquals(listOf(3_000L, 4_000L), restored.map { it.occurredAtEpochMillis })
            assertEquals(3, restored.first().attemptCount)
            assertEquals(9_000L, restored.first().nextAttemptAtEpochMillis)
            assertEquals("HTTP500", restored.first().lastErrorCode)
            assertEquals(AlarmOccurrenceOutboxState.BLOCKED, restored.last().state)
            assertEquals("권한 확인", restored.last().lastErrorMessage)
            assertEquals(1, dao.getUnsentEvents("other").size)
            assertTrue(dao.saveDeliveryState(Owner, restored.first().id, AlarmOccurrenceOutboxState.SENT, 4))
            assertEquals(1, dao.getUnsentEvents(Owner).size)
        } finally {
            database.close()
            listOf(path, "$path-wal", "$path-shm").forEach { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        }
    }

    @Test
    fun `실제 울림 시각이 늦게 도착하면 누락 값만 채우고 보류 상태와 기존 값은 보존한다`() = withDatabase { dao ->
        dao.recordStart(execution().copy(startedAtEpochMillis = null))
        val startId = dao.getEvents(Owner, Root).single().id
        dao.saveDeliveryState(Owner, startId, AlarmOccurrenceOutboxState.BLOCKED, 0, errorCode = "MISSING_STARTED_AT")
        dao.recordStart(execution())
        dao.recordStart(execution().copy(startedAtEpochMillis = 99_000))
        assertEquals(1_000L, dao.getExecution(Owner, Root)?.startedAtEpochMillis)
        assertEquals(1_000L, dao.getRinging(Owner, Root)?.ringingAtEpochMillis)
        assertEquals(1_000L, dao.getEvents(Owner, Root).single().occurredAtEpochMillis)
        assertEquals(AlarmOccurrenceOutboxState.BLOCKED, dao.getEvents(Owner, Root).single().state)
        dao.recordRepeat(Owner, Root, "retry-1", "event-1", null)
        dao.recordRepeat(Owner, Root, "retry-1", "regenerated-event", 3_000)
        assertEquals("event-1", dao.getRinging(Owner, "retry-1")?.eventId)
        assertEquals(3_000L, dao.getEvents(Owner, Root).last().occurredAtEpochMillis)
    }

    @Test
    fun `확인하지 못한 시작 시각은 예정 시각이나 해제 시각으로 채우지 않는다`() = withDatabase { dao ->
        dao.recordStart(execution().copy(startedAtEpochMillis = null))
        dao.recordDismissal(Owner, Root, 2_000)
        assertNull(dao.getExecution(Owner, Root)?.startedAtEpochMillis)
        assertNull(dao.getRinging(Owner, Root)?.ringingAtEpochMillis)
        assertNull(dao.getEvents(Owner, Root).first().occurredAtEpochMillis)
        val id = dao.getEvents(Owner, Root).first().id
        assertTrue(dao.saveDeliveryState(Owner, id, AlarmOccurrenceOutboxState.BLOCKED, 0, errorCode = "MISSING_STARTED_AT"))
        assertEquals(2, dao.getUnsentEvents(Owner).size)
    }

    private fun withDatabase(block: suspend (AlarmOccurrenceSyncDao) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
        try { block(db.alarmOccurrenceSyncDao()) } finally { db.close() }
    }
}

private const val Owner = "account-1"
private const val Root = "initial-local-ring"
private const val ServerId = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f"
private fun execution() = AlarmOccurrenceSyncEntity(Root, Owner, "alarm-1", 1, 900, 1_000)
private fun openDatabase(path: String) = buildRingoutDatabase(Room.databaseBuilder<RingoutDatabase>(name = path))
