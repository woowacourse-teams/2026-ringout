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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class IosAlarmOccurrenceRecorderTest {
    @Test
    fun `직전 해제 시각이 늦게 도착하면 이미 확인된 재울림 예정 시각을 보완한다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("first", "07:03").copy(ringingStoppedAtEpochMillis = null))
        f.outcomes.recordRetryRingingSchedule("retry", "first", 5)
        f.outcomes.recordRingingTimes(stop("retry", "07:09", retry = 1, chainId = "first"))
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
        f.outcomes.recordRingingTimes(stop("retry", "07:09", retry = 1, chainId = "first").copy(ownerAccountId = "2"))
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
    fun `명시적 게스트 체인은 로컬 전용이고 미확인 이전 이벤트는 대기로 보존한다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("guest", "07:03").copy(ownerAccountId = null))
        f.outcomes.recordRetryRingingSchedule("retry", "guest", 5)
        assertTrue(f.outcomes.recordRingingTimes(stop("retry", "07:09", retry = 1, chainId = "guest")))
        f.outcomes.recordRingingTimes(stop("legacy", "07:10").copy(ownerCaptured = false))
        f.outcomes.recordFailure("retry", "2026-10-01", time("07:15"))
        assertEquals(IosAlarmOccurrenceRecordState.LOCAL_ONLY, f.dao.getIosRecordDecision("guest")?.state)
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("legacy")?.state)
        assertNull(f.dao.findExecutionForRecording("legacy"))
    }

    @Test
    fun `예약 소유 계정은 현재 로그인 계정과 달라도 실행 소유자로 유지한다`() = withFixture { f ->
        f.tokens.userId = 2
        f.outcomes.recordRingingTimes(stop("current-account", "07:03").copy(ownerAccountId = "1"))
        assertEquals("1", f.dao.getExecution("1", "current-account")?.ownerAccountId)

        f.tokens.userId = 3
        f.session.startNewSession()
        f.outcomes.recordRingingTimes(stop("current-account", "07:04").copy(ownerAccountId = "3"))
        assertEquals("1", f.dao.getExecution("1", "current-account")?.ownerAccountId)
        assertNull(f.dao.getExecution("3", "current-account"))
    }

    @Test
    fun `소유권 미확정 체인은 로그인 후에도 대기로 보존하고 ACK하지 않는다`() = withFixture { f ->
        f.session.clear()
        f.outcomes.recordRingingTimes(stop("local-root", "07:03").copy(ownerCaptured = false, ownerAccountId = null))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("local-root")?.state)
        assertNull(f.dao.findExecutionForRecording("local-root"))

        f.tokens.userId = 2
        f.session.startNewSession()
        f.recorder.resolvePendingRecordDecisions()
        assertFalse(f.outcomes.recordRecoveryAttempt(recoveryAttempt(
            id = "local-root", clock = "07:03", scheduledAt = "07:00", ownerCaptured = false,
        )))
        assertFalse(f.outcomes.recordRingingTimes(
            stop("local-root", "07:03").copy(ownerCaptured = false, ownerAccountId = null),
        ))
        f.outcomes.recordFailure("local-root", "2026-10-01", time("07:15"))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("local-root")?.state)
        assertNull(f.dao.findExecutionForRecording("local-root"))
        assertTrue(f.dao.getEvents("2", "local-root").isEmpty())
    }

    @Test
    fun `재인증 후에도 예약 소유권이 없으면 판단 대기로 남는다`() = withFixture { f ->
        f.session.requireReauthentication()
        assertEquals(false, f.outcomes.recordRecoveryAttempt(recoveryAttempt(
            id = "pending-root", clock = "07:03", scheduledAt = "07:00", ownerCaptured = false,
        )))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("pending-root")?.state)
        assertNull(f.dao.findExecutionForRecording("pending-root"))

        f.tokens.userId = 4
        f.session.startNewSession()
        f.recorder.resolvePendingRecordDecisions()
        assertNull(f.dao.findExecutionForRecording("pending-root"))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("pending-root")?.state)
    }

    @Test
    fun `인증 복원 중 기록은 로그인 완료 뒤에도 소유권이 확인될 때까지 대기한다`() = withFixture { f ->
        val restoringSession = AuthSession()
        val recorder = IosAlarmOccurrenceRecorder(
            f.dao, f.db.alarmActivityDao(), f.alarms,
        )
        val outcomes = RoomIosMissionOutcomeRecorder(
            RecordMissionResult(DefaultMissionHistoryRepository(RoomMissionHistoryDataSource(f.db.missionHistoryDao()))),
            f.db.alarmActivityDao(), f.alarms, recorder,
        )

        assertEquals(false, outcomes.recordRingingTimes(
            stop("restoring-root", "07:03").copy(ownerCaptured = false),
        ))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("restoring-root")?.state)
        assertNull(f.dao.findExecutionForRecording("restoring-root"))

        f.tokens.userId = 5
        restoringSession.markAuthenticated()
        recorder.resolvePendingRecordDecisions()
        assertNull(f.dao.findExecutionForRecording("restoring-root"))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("restoring-root")?.state)
    }

    @Test
    fun `대기 체인은 일치하는 예약의 저장 소유자가 확인되면 해당 계정으로 재생한다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("owned-later", "07:03").copy(ownerCaptured = false, ownerAccountId = null))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("owned-later")?.state)

        f.alarms.replace(settings().copy(request = settings().request.copy(ownerAccountId = "9")))
        f.recorder.resolvePendingRecordDecisions()

        assertEquals("9", f.dao.getExecution("9", "owned-later")?.ownerAccountId)
        assertEquals(IosAlarmOccurrenceRecordState.SERVER, f.dao.getIosRecordDecision("owned-later")?.state)
    }

    @Test
    fun `재울림 연결은 기록기 재생성 후에도 유지하며 강제종료는 실제 완료 시각을 기록한다`() = withFixture { f ->
        f.outcomes.recordRingingTimes(stop("first", "07:03"))
        f.outcomes.recordRetryRingingSchedule("retry", "first", 5)
        val originalId = assertNotNull(f.dao.getRinging("1", "retry")?.eventId)
        val restored = IosAlarmOccurrenceRecorder(f.dao, f.db.alarmActivityDao(), f.alarms)
        restored.registerRetry("retry", "first")
        assertTrue(restored.recordStopped(stop("retry", "07:09", retry = 1, chainId = "first")))
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

    @Test
    fun `복구 시 다음 재울림 예약은 저널 토큰으로 연결만 저장하고 실제 반복 울림은 재울림 도착 때 기록한다`() = withFixture { f ->
        val rootAck = f.outcomes.recordRecoveryAttempt(recoveryAttempt(
            id = "root",
            clock = "07:03",
            scheduledAt = "07:00",
            nextId = "retry",
            nextEventId = "retry-token",
            nextDue = "07:08",
        ))
        assertTrue(rootAck)
        assertNotNull(f.dao.getRinging("1", "retry")?.eventId)
        assertTrue(f.dao.getRinging("1", "retry")?.eventId != "retry")
        assertEquals(listOf(AlarmOccurrenceOutboxKind.START, AlarmOccurrenceOutboxKind.INITIAL_DISMISSED),
            f.dao.getEvents("1", "root").map { it.kind })

        val retryAck = f.outcomes.recordRecoveryAttempt(recoveryAttempt(
            id = "retry",
            clock = "07:09",
            scheduledAt = "07:08",
            retry = 1,
        ))
        assertTrue(retryAck)
        assertEquals(
            listOf(
                AlarmOccurrenceOutboxKind.START,
                AlarmOccurrenceOutboxKind.INITIAL_DISMISSED,
                AlarmOccurrenceOutboxKind.REPEAT_RANG,
                AlarmOccurrenceOutboxKind.REPEAT_DISMISSED,
            ),
            f.dao.getEvents("1", "root").map { it.kind },
        )
        assertEquals(time("07:08"), f.dao.getEvents("1", "root").single { it.kind == AlarmOccurrenceOutboxKind.REPEAT_RANG }.occurredAtEpochMillis)
    }

    @Test
    fun `다른 알람이나 예약 버전의 재울림 복구는 기존 체인에 연결하지 않는다`() = withFixture { f ->
        assertTrue(f.outcomes.recordRecoveryAttempt(recoveryAttempt(
            id = "root",
            clock = "07:03",
            scheduledAt = "07:00",
            nextId = "retry",
            nextEventId = "retry-token",
            nextDue = "07:08",
        )))
        val validRetry = recoveryAttempt(
            id = "retry",
            clock = "07:09",
            scheduledAt = "07:08",
            retry = 1,
        )

        assertEquals(false, f.outcomes.recordRecoveryAttempt(validRetry.copy(
            snapshot = validRetry.snapshot.copy(scheduleVersion = 2),
        )))
        assertEquals(listOf(AlarmOccurrenceOutboxKind.START, AlarmOccurrenceOutboxKind.INITIAL_DISMISSED),
            f.dao.getEvents("1", "root").map { it.kind })
    }

    @Test
    fun `복구 이벤트는 소유권 근거별로 ACK 여부를 구분한다`() = withFixture { f ->
        assertTrue(f.outcomes.recordRecoveryAttempt(recoveryAttempt("guest", "07:03", "07:00", owner = null)))
        assertNull(f.dao.findExecutionForRecording("guest"))

        assertEquals(false, f.outcomes.recordRecoveryAttempt(recoveryAttempt("unknown", "07:04", "07:00", ownerCaptured = false)))
        assertEquals(IosAlarmOccurrenceRecordState.AUTH_PENDING, f.dao.getIosRecordDecision("unknown")?.state)
        assertNull(f.dao.findExecutionForRecording("unknown"))

        assertEquals(false, f.outcomes.recordRecoveryAttempt(recoveryAttempt("orphan-retry", "07:09", "07:08", retry = 1)))
        assertNull(f.dao.findExecutionForRecording("orphan-retry"))
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
    val tokens = Tokens()
    val session = AuthSession().apply { markAuthenticated() }
    val recorder = IosAlarmOccurrenceRecorder(dao, db.alarmActivityDao(), alarms)
    val outcomes = RoomIosMissionOutcomeRecorder(
        RecordMissionResult(DefaultMissionHistoryRepository(RoomMissionHistoryDataSource(db.missionHistoryDao()))),
        db.alarmActivityDao(), alarms, recorder,
    )
}

private class Tokens(var userId: Long = 1) : SecureTokenStorage {
    override suspend fun read() = AuthTokens("header." + Base64.UrlSafe.encode(
        """{"sub":"$userId","userId":$userId,"tokenType":"ACCESS"}""".encodeToByteArray()).trimEnd('=') + ".signature", "refresh")
    override suspend fun save(tokens: AuthTokens) = Unit
    override suspend fun clear() = Unit
}

private fun settings() = SavedAlarmSchedule(AlarmScheduleRequest(
    id = "alarm", time = "07:00", selectedDays = listOf("목"), repeatEnabled = true, limitMinutes = 5,
    destinationLabel = "목적지", destinationLatitude = 37.5, destinationLongitude = 127.0,
    alarmSoundName = "기본", alarmSoundUri = null,
), enabled = true)

private fun stop(id: String, clock: String, retry: Int = 0, chainId: String? = null) = IosAlarmMissionEventDto(
    eventId = "event-$id", alarmId = "alarm", occurrenceId = id, action = IosAlarmMissionAction.STOP,
    occurredAtEpochMillis = time(clock), retryAttempt = retry, ringingStoppedAtEpochMillis = time(clock),
    ownerAccountId = "1", ownerCaptured = true,
    chainId = chainId,
)

private fun recoveryAttempt(
    id: String,
    clock: String,
    scheduledAt: String,
    retry: Int = 0,
    owner: String? = "1",
    ownerCaptured: Boolean = true,
    nextId: String? = null,
    nextEventId: String? = null,
    nextDue: String? = null,
) = IosAlarmRecoveryAttemptDto(
    occurrenceId = id, chainId = if (retry == 0) id else "root", sequence = retry,
    snapshot = IosAlarmRecoverySourceSnapshotDto(
        sourceAlarmId = "alarm", scheduleVersion = 1,
        destination = IosAlarmRecoveryDestinationSnapshotDto("목적지", 37.5, 127.0),
        limitSeconds = 300, originalTime = "07:00",
        owner = IosAlarmRecoveryOwnerDto(when {
            !ownerCaptured -> IosAlarmRecoveryOwnerKind.UNCAPTURED
            owner == null -> IosAlarmRecoveryOwnerKind.CAPTURED_GUEST
            else -> IosAlarmRecoveryOwnerKind.CAPTURED_ACCOUNT
        }, owner),
    ),
    stopAtEpochMillis = time(clock), scheduledAtEpochMillis = time(scheduledAt),
    expiresAtEpochMillis = nextDue?.let(::time) ?: time(clock) + 300_000,
    nextReservationUuid = "reserved-$id", nextOccurrenceToken = nextId ?: "next-$id",
    currentReservation = IosAlarmRecoveryReservationDto("reserved-$id", IosAlarmRecoveryReservationStatus.CONFIRMED),
    missionProjectionAck = false, recordProjectionAck = false, outcome = IosAlarmRecoveryAttemptOutcome.ACTIVE,
)

private fun time(clock: String): Long = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    dateFormat = "yyyy-MM-dd HH:mm"
}.dateFromString("2026-10-01 $clock")!!.let { ((it.timeIntervalSinceReferenceDate + 978_307_200) * 1_000).toLong() }
private fun iso(clock: String) = Instant.fromEpochMilliseconds(time(clock)).toString()
