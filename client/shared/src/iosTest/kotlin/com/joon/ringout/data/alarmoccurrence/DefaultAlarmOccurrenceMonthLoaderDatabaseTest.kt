package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.database.RoomLocalUserDataCleanup
import com.joon.ringout.data.destination.DestinationSyncSessionGate
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultAlarmOccurrenceMonthLoaderDatabaseTest {
    @Test
    fun `동시 조회는 한 요청을 공유하고 빈 완료 월과 미래 월은 요청하지 않는다`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
        val requested = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requestCount = 0
        val client = HttpClient(MockEngine {
            requestCount++
            requested.complete(Unit)
            release.await()
            respondMonth(monthResponse())
        }) { configureRingoutHttpClient() }

        try {
            val dao = database.alarmOccurrenceCacheDao()
            val session = AuthSession().apply { startNewSession() }
            val storage = MonthlyTokenStorage()
            val gate = DestinationSyncSessionGate()
            val loader = DefaultAlarmOccurrenceMonthLoader(client, storage, session, dao, gate, backgroundScope) { December }
            val first = async { loader.ensure(December) }
            requested.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) { loader.ensure(December) }
            release.complete(Unit)
            assertTrue(first.await())
            assertTrue(second.await())
            assertTrue(dao.isComplete("1", "2026-12"))

            val reopenedLoader = DefaultAlarmOccurrenceMonthLoader(client, storage, session, dao, gate, backgroundScope) { December }
            assertTrue(reopenedLoader.ensure(December))
            assertTrue(reopenedLoader.ensure(MissionYearMonth(2027, 1)))
            assertEquals(1, requestCount)
        } finally {
            client.close()
            database.close()
        }
    }

    @Test
    fun `불완전한 월을 재시도하고 로그아웃 뒤 도착한 이전 세션의 응답은 저장하지 않는다`() = runTest {
        val database = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
        val requested = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var requestCount = 0
        val client = HttpClient(MockEngine {
            when (++requestCount) {
                1 -> respondMonth("""{"isSuccess":true,"code":"OK","message":"OK","result":{"month":"2026-12","days":[]}}""")
                2 -> {
                    requested.complete(Unit)
                    release.await()
                    respondMonth(monthResponse(occurrences = DecemberOccurrence))
                }
                else -> respondMonth(monthResponse(occurrences = DecemberOccurrence))
            }
        }) { configureRingoutHttpClient() }

        try {
            val dao = database.alarmOccurrenceCacheDao()
            val session = AuthSession().apply { startNewSession() }
            val storage = MonthlyTokenStorage()
            val gate = DestinationSyncSessionGate()
            val cleanup = RoomLocalUserDataCleanup(database.localUserDataCleanupDao(), gate) { session.identity.value }
            val loader = DefaultAlarmOccurrenceMonthLoader(client, storage, session, dao, gate, backgroundScope) { December }
            assertFalse(loader.ensure(December))
            assertFalse(dao.isComplete("1", "2026-12"))

            val previousSession = async { loader.ensure(December) }
            requested.await()
            cleanup.clear()
            session.clear()
            session.startNewSession()
            release.complete(Unit)
            assertFalse(previousSession.await())
            assertFalse(dao.isComplete("1", "2026-12"))
            assertTrue(loader.ensure(December))
            assertTrue(dao.isComplete("1", "2026-12"))
            assertEquals(3, requestCount)
        } finally {
            client.close()
            database.close()
        }
    }
}
