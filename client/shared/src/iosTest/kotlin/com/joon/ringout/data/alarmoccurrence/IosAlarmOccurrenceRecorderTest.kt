package com.joon.ringout.data.alarmoccurrence

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.joon.ringout.alarm.*
import com.joon.ringout.data.alarm.RoomAlarmDataSource
import com.joon.ringout.data.database.RingoutDatabase
import com.joon.ringout.data.missionhistory.DefaultMissionHistoryRepository
import com.joon.ringout.data.missionhistory.RoomMissionHistoryDataSource
import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.data.network.configureRingoutHttpClient
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthTokens
import com.joon.ringout.domain.auth.SecureTokenStorage
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class IosAlarmOccurrenceRecorderTest {
    @Test
    fun `직전 해제 시각이 늦게 도착하면 이미 확인된 재울림 예정 시각을 보완한다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("first", "07:03").copy(ringingStoppedAtEpochMillis = null))
        f.outcomes.recordRetryRingingSchedule("retry", "first", 5)
        f.outcomes.recordRingingTimes(stop("retry", "07:09", retry = 1))
        val before = f.dao.getRinging("1", "retry")
        assertNull(before?.ringingAtEpochMillis)
        f.outcomes.recordRingingTimes(stop("first", "07:03"))
        f.outcomes.recordRetryRingingSchedule("retry", "first", 5)
        assertEquals(before?.eventId, f.dao.getRinging("1", "retry")?.eventId)
        assertEquals(time("07:08"), f.dao.getRinging("1", "retry")?.ringingAtEpochMillis)
        assertEquals(time("07:08"), f.dao.getEvents("1", "first").single { it.kind == AlarmOccurrenceOutboxKind.REPEAT_RANG }.occurredAtEpochMillis)
    }

    @Test
    fun `관찰 없이 해제한 알람도 예정 시각으로 POST한 뒤 실제 해제와 재울림과 도착을 PATCH한다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("first", "07:03"))
        f.outcomes.recordRetryRingingSchedule("retry", "first", 5)
        assertEquals(2, f.dao.getEvents("1", "first").size) // 예약만으로 REPEAT_RANG을 만들지 않는다.
        f.outcomes.recordRingingTimes(stop("retry", "07:09", retry = 1).copy(ownerAccountId = "2"))
        f.outcomes.recordSuccess("retry", "2026-10-01", time("07:12"))

        val calls = mutableListOf<Pair<HttpMethod, JsonObject>>()
        val client = HttpClient(MockEngine { request ->
            if (request.method == HttpMethod.Get) {
                return@MockEngine respond("""{"isSuccess":true,"code":"ROOM200","message":"OK","result":{"rooms":[]}}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
            calls += request.method to ApiJson.parseToJsonElement((request.body as TextContent).text).jsonObject
            respond("""{"isSuccess":true,"code":"OK","message":"OK","result":{
                "alarmOccurrenceId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","startedAt":"${iso("07:00")}","ringings":[]}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { configureRingoutHttpClient() }
        try {
            val session = AuthSession().apply { markAuthenticated() }
            AlarmOccurrenceSyncer(f.dao, client, Tokens(), session).flush()
            assertEquals(listOf(HttpMethod.Post, HttpMethod.Patch, HttpMethod.Patch, HttpMethod.Patch, HttpMethod.Patch), calls.map { it.first })
            assertEquals(iso("07:00"), calls[0].second["scheduledAt"]?.jsonPrimitive?.content)
            assertEquals(iso("07:00"), calls[0].second["startedAt"]?.jsonPrimitive?.content)
            assertEquals(iso("07:03"), calls[1].second["dismissedAt"]?.jsonPrimitive?.content)
            assertEquals(iso("07:08"), calls[2].second["ringingAt"]?.jsonPrimitive?.content)
            assertEquals(calls[2].second["eventId"], calls[3].second["eventId"])
            assertEquals(iso("07:09"), calls[3].second["dismissedAt"]?.jsonPrimitive?.content)
            assertEquals(iso("07:12"), calls[4].second["arrivedAt"]?.jsonPrimitive?.content)
            assertTrue(f.dao.getUnsentEvents("1").isEmpty())
            assertTrue(f.dao.getUnsentEvents("2").isEmpty())
        } finally { client.close() }
    }

    @Test
    fun `울림 관찰 시각은 로컬에 유지하고 서버 시작은 예정 시각으로 고정한다`() = withFixture { f ->
        f.recorder.recordObserved(IosRingingAlarm("alarm", "alarm", "first", 0, "07:00", "목적지", 5,
            time("07:02")), "1", true)
        f.outcomes.recordRingingTimes(stop("first", "07:03").copy(ringingObservedAtEpochMillis = time("07:02")))
        assertEquals(time("07:00"), f.dao.getExecution("1", "first")?.startedAtEpochMillis)
        assertEquals(time("07:02"), f.db.alarmActivityDao().getOccurrenceTimes("first")?.ringingStartedAtEpochMillis)
        f.alarms.replace(settings().copy(request = settings().request.copy(time = "06:00")))
        f.outcomes.recordRingingTimes(stop("first", "07:04").copy(ownerAccountId = "2"))
        assertEquals(time("07:00"), f.dao.getExecution("1", "first")?.startedAtEpochMillis)
        assertEquals(time("07:03"), f.dao.getEvents("1", "first").last().occurredAtEpochMillis)
        assertNull(f.dao.getExecution("2", "first"))
    }

    @Test
    fun `게스트와 계정 캡처가 없는 이전 이벤트는 서버 기록을 만들지 않는다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("guest", "07:03").copy(ownerAccountId = null))
        f.outcomes.recordRetryRingingSchedule("retry", "guest", 5)
        f.outcomes.recordRingingTimes(stop("retry", "07:09", retry = 1))
        f.outcomes.recordRingingTimes(stop("legacy", "07:10").copy(ownerCaptured = false))
        f.outcomes.recordFailure("retry", "2026-10-01", time("07:15"))
        assertTrue(f.dao.getUnsentEvents("1").isEmpty())
    }

    @Test
    fun `재울림 연결은 기록기 재생성 후에도 유지하며 강제종료는 실제 완료 시각을 기록한다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("first", "07:03"))
        f.outcomes.recordRetryRingingSchedule("retry", "first", 5)
        val originalId = f.dao.getRinging("1", "retry")?.eventId
        val restored = IosAlarmOccurrenceRecorder(f.dao, f.db.alarmActivityDao(), f.alarms)
        restored.registerRetry("retry", "first")
        restored.recordStopped(stop("retry", "07:09", retry = 1))
        restored.recordTerminal("retry", time("07:11"), arrived = false)
        assertEquals(originalId, f.dao.getRinging("1", "retry")?.eventId)
        assertEquals(time("07:08"), f.dao.getRinging("1", "retry")?.ringingAtEpochMillis)
        assertEquals(AlarmOccurrenceOutboxKind.FORCE_ENDED, f.dao.getEvents("1", "first").last().kind)
        assertEquals(time("07:11"), f.dao.getEvents("1", "first").last().occurredAtEpochMillis)
    }

    @Test
    fun `설정 시각을 알 수 없거나 버전이 다르면 해제 시각으로 시작을 만들지 않는다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("old-version", "07:03").copy(scheduleVersion = 2))
        assertNull(f.dao.getExecution("1", "old-version")?.startedAtEpochMillis)
        assertNull(f.dao.getExecution("1", "old-version")?.scheduledAtEpochMillis)
        assertEquals(time("07:03"), f.dao.getEvents("1", "old-version").last().occurredAtEpochMillis)
    }

    @Test
    fun `자정 이후 해제한 알람은 전날 예정 시각으로 생성한다`() = withFixture { f ->
        f.alarms.replace(settings().copy(request = settings().request.copy(time = "23:58")))
        f.outcomes.recordRingingTimes(stop("midnight", "00:01"))
        val expected = iosInitialRingingScheduledAt("23:58", time("00:01"))
        assertEquals(expected, f.dao.getExecution("1", "midnight")?.startedAtEpochMillis)
        assertEquals(expected, f.dao.getExecution("1", "midnight")?.scheduledAtEpochMillis)
    }

    private fun withFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val db = Room.inMemoryDatabaseBuilder<RingoutDatabase>().setDriver(BundledSQLiteDriver()).build()
        try {
            val f = Fixture(db)
            f.alarms.replace(settings())
            block(f)
        } finally { db.close() }
    }
}

private class Fixture(val db: RingoutDatabase) {
    val dao = db.alarmOccurrenceSyncDao()
    val alarms = RoomAlarmDataSource(db.alarmDao())
    val recorder = IosAlarmOccurrenceRecorder(dao, db.alarmActivityDao(), alarms)
    val outcomes = RoomIosMissionOutcomeRecorder(
        RecordMissionResult(DefaultMissionHistoryRepository(RoomMissionHistoryDataSource(db.missionHistoryDao()))),
        db.alarmActivityDao(), alarms, recorder,
    )
}

private class Tokens : SecureTokenStorage {
    override suspend fun read() = AuthTokens("header." + Base64.UrlSafe.encode(
        """{"sub":"1","userId":1,"tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=') + ".signature", "refresh")
    override suspend fun save(tokens: AuthTokens) = Unit
    override suspend fun clear() = Unit
}

private fun settings() = SavedAlarmSchedule(AlarmScheduleRequest(
    id = "alarm", time = "07:00", selectedDays = listOf("목"), repeatEnabled = true, limitMinutes = 5,
    destinationName = "목적지", destinationAddress = "서울", destinationLatitude = 37.5, destinationLongitude = 127.0,
    alarmSoundName = "기본", alarmSoundUri = null,
), enabled = true)

private fun stop(id: String, clock: String, retry: Int = 0) = IosAlarmMissionEventDto(
    eventId = "event-$id", alarmId = "alarm", occurrenceId = id, action = IosAlarmMissionAction.STOP,
    occurredAtEpochMillis = time(clock), retryAttempt = retry, ringingStoppedAtEpochMillis = time(clock),
    ownerAccountId = "1", ownerCaptured = true,
)

private fun time(clock: String): Long = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    dateFormat = "yyyy-MM-dd HH:mm"
}.dateFromString("2026-10-01 $clock")!!.let { ((it.timeIntervalSinceReferenceDate + 978_307_200) * 1_000).toLong() }
private fun iso(clock: String) = Instant.fromEpochMilliseconds(time(clock)).toString()
