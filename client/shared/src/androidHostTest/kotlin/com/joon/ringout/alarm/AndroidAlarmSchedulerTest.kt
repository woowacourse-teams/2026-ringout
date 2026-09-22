package com.joon.ringout.alarm

import com.joon.ringout.analytics.AlarmSettingsAnalyticsContext
import com.joon.ringout.analytics.AlarmSoundDisplaySelection
import com.joon.ringout.analytics.AnalyticsAlarmSoundSurface
import com.joon.ringout.data.alarm.AlarmDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidAlarmSchedulerTest {
    @Test
    fun `신규 저장은 생성으로 기록하고 이후 성공한 재설정마다 업데이트를 기록한다`() = runBlocking {
        val dataSource = FakeAlarmDataSource()
        val gateway = FakeAndroidAlarmGateway()
        val analytics = RecordingAndroidAlarmAnalytics()
        val scheduler = AndroidAlarmScheduler(dataSource, gateway, analytics = analytics)
        val context = AlarmSettingsAnalyticsContext(
            soundDisplaySelection = AlarmSoundDisplaySelection(
                surface = AnalyticsAlarmSoundSurface.EditorPicker,
                position = 2,
                listSize = 3,
                selectionChanged = true,
            ),
        )

        scheduler.schedule(request(), context)
        scheduler.schedule(request(time = "08:10"), context)
        scheduler.schedule(request(time = "09:20"), context)

        assertEquals(
            listOf("created", "updated", "updated"),
            analytics.events.map(RecordingAndroidAlarmAnalytics.Event::name),
        )
        assertEquals(
            listOf("07:05", "08:10", "09:20"),
            analytics.events.map { event -> event.request.time },
        )
        assertEquals(context, analytics.events[1].context)
        assertEquals(context, analytics.events[2].context)
    }

    @Test
    fun `예약 또는 Room 저장 실패는 생성과 업데이트 이벤트를 기록하지 않는다`() = runBlocking {
        val analytics = RecordingAndroidAlarmAnalytics()
        val gateway = FakeAndroidAlarmGateway().apply {
            scheduleFailure = IllegalStateException("schedule failed")
        }
        val scheduler = AndroidAlarmScheduler(
            dataSource = FakeAlarmDataSource(),
            alarmGateway = gateway,
            analytics = analytics,
        )

        assertFailsWith<Exception> { scheduler.schedule(request()) }

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `Room 최종 커밋 실패는 업데이트 이벤트를 기록하지 않는다`() = runBlocking {
        val previous = request()
        val latest = previous.copy(time = "08:10")
        val analytics = RecordingAndroidAlarmAnalytics()
        val scheduler = AndroidAlarmScheduler(
            dataSource = FakeAlarmDataSource(
                initial = listOf(SavedAlarmSchedule(previous, enabled = true)),
                replaceFailure = { alarm -> alarm.enabled && alarm.request == latest },
            ),
            alarmGateway = FakeAndroidAlarmGateway(),
            analytics = analytics,
        )

        assertFailsWith<IllegalStateException> { scheduler.schedule(latest) }

        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `비활성 기존 알람 수정 저장은 최신 요청으로 예약하고 활성 상태로 저장한다`() = runBlocking {
        val previous = request()
        val latest = previous.copy(
            time = "08:10",
            selectedDays = listOf("화", "목"),
            limitMinutes = 20,
            destinationName = "집",
            alarmSoundUri = "content://ringout/alarm/wave",
        )
        val dataSource = FakeAlarmDataSource(
            initial = listOf(SavedAlarmSchedule(previous, enabled = false)),
        )
        val gateway = FakeAndroidAlarmGateway()
        val scheduler = AndroidAlarmScheduler(dataSource, gateway)

        scheduler.schedule(latest)

        val stored = dataSource.getById(latest.id)!!
        assertTrue(stored.enabled)
        assertEquals(latest, stored.request)
        assertEquals(latest, gateway.scheduled[latest.id])
        assertEquals(listOf("schedule:alarm-1:08:10"), gateway.events)
    }

    @Test
    fun `활성 기존 알람 수정 저장은 같은 ID의 최신 예약 하나만 남긴다`() = runBlocking {
        val previous = request()
        val latest = previous.copy(time = "09:20", destinationAddress = "서울특별시 강남구")
        val dataSource = FakeAlarmDataSource(
            initial = listOf(SavedAlarmSchedule(previous, enabled = true)),
        )
        val gateway = FakeAndroidAlarmGateway(
            initial = mapOf(previous.id to previous),
        )
        val scheduler = AndroidAlarmScheduler(dataSource, gateway)

        scheduler.schedule(latest)

        assertEquals(mapOf(latest.id to latest), gateway.scheduled)
        assertEquals(latest, dataSource.getById(latest.id)!!.request)
        assertTrue(dataSource.getById(latest.id)!!.enabled)
        assertEquals(
            listOf(
                SavedAlarmSchedule(previous, enabled = false),
                SavedAlarmSchedule(latest, enabled = true),
            ),
            dataSource.replaceHistory,
        )
    }

    @Test
    fun `최신 예약 실패는 이전 비활성 스냅샷을 복원하고 시스템 예약을 남기지 않는다`() = runBlocking {
        val previous = request()
        val latest = previous.copy(time = "08:10")
        val dataSource = FakeAlarmDataSource(
            initial = listOf(SavedAlarmSchedule(previous, enabled = false)),
        )
        val gateway = FakeAndroidAlarmGateway().apply {
            scheduleFailure = IllegalStateException("schedule failed")
        }
        val scheduler = AndroidAlarmScheduler(dataSource, gateway)

        assertFailsWith<Exception> {
            scheduler.schedule(latest)
        }

        assertEquals(SavedAlarmSchedule(previous, enabled = false), dataSource.getById(previous.id))
        assertFalse(previous.id in gateway.scheduled)
        assertEquals(listOf("schedule:alarm-1:08:10", "cancel:alarm-1"), gateway.events)
    }

    @Test
    fun `최신 예약 실패 후 이전 활성 예약 복구도 실패하면 Room을 비활성으로 낮춘다`() = runBlocking {
        val previous = request()
        val latest = previous.copy(time = "08:10")
        val dataSource = FakeAlarmDataSource(
            initial = listOf(SavedAlarmSchedule(previous, enabled = true)),
        )
        val gateway = FakeAndroidAlarmGateway(
            initial = mapOf(previous.id to previous),
        ).apply {
            scheduleFailure = IllegalStateException("schedule failed")
        }
        val scheduler = AndroidAlarmScheduler(dataSource, gateway)

        assertFailsWith<IllegalStateException> {
            scheduler.schedule(latest)
        }

        assertEquals(SavedAlarmSchedule(previous, enabled = false), dataSource.getById(previous.id))
        assertFalse(previous.id in gateway.scheduled)
        assertEquals(
            listOf(
                "schedule:alarm-1:08:10",
                "cancel:alarm-1",
                "schedule:alarm-1:07:05",
            ),
            gateway.events,
        )
    }

    @Test
    fun `Room 최종 커밋 실패는 새 예약을 제거하고 이전 활성 예약을 복원한다`() = runBlocking {
        val previous = request()
        val latest = previous.copy(time = "08:10")
        val dataSource = FakeAlarmDataSource(
            initial = listOf(SavedAlarmSchedule(previous, enabled = true)),
            replaceFailure = { alarm -> alarm.enabled && alarm.request == latest },
        )
        val gateway = FakeAndroidAlarmGateway()
        val scheduler = AndroidAlarmScheduler(dataSource, gateway)

        assertFailsWith<IllegalStateException> {
            scheduler.schedule(latest)
        }

        assertEquals(SavedAlarmSchedule(previous, enabled = true), dataSource.getById(previous.id))
        assertEquals(previous, gateway.scheduled[previous.id])
        assertEquals(
            listOf(
                "schedule:alarm-1:08:10",
                "cancel:alarm-1",
                "schedule:alarm-1:07:05",
            ),
            gateway.events,
        )
    }

    private fun request(
        time: String = "07:05",
    ) = AlarmScheduleRequest(
        id = "alarm-1",
        time = time,
        selectedDays = listOf("월", "금"),
        repeatEnabled = true,
        limitMinutes = 12,
        destinationName = "회사",
        destinationAddress = "서울특별시 중구 세종대로 110",
        destinationLatitude = 37.5665,
        destinationLongitude = 126.978,
        targetDistanceKm = 1.2,
        alarmSoundName = "기본 알람음",
        alarmSoundUri = null,
    )
}

private class FakeAlarmDataSource(
    initial: List<SavedAlarmSchedule> = emptyList(),
    private val replaceFailure: (SavedAlarmSchedule) -> Boolean = { false },
) : AlarmDataSource {
    private val alarms = linkedMapOf<String, SavedAlarmSchedule>()
    private val observed = MutableStateFlow<List<SavedAlarmSchedule>>(emptyList())
    val replaceHistory = mutableListOf<SavedAlarmSchedule>()

    init {
        initial.forEach { alarm -> alarms[alarm.request.id] = alarm }
        observed.value = alarms.values.toList()
    }

    override fun observeAll(): Flow<List<SavedAlarmSchedule>> = observed

    override suspend fun getAll(): List<SavedAlarmSchedule> = alarms.values.toList()

    override suspend fun getEnabled(): List<SavedAlarmSchedule> =
        alarms.values.filter(SavedAlarmSchedule::enabled)

    override suspend fun getById(id: String): SavedAlarmSchedule? = alarms[id]

    override suspend fun replace(alarm: SavedAlarmSchedule) {
        if (replaceFailure(alarm)) throw IllegalStateException("write failed")
        replaceHistory += alarm
        alarms[alarm.request.id] = alarm
        observed.value = alarms.values.toList()
    }

    override suspend fun setEnabled(id: String, enabled: Boolean): Boolean {
        val alarm = alarms[id] ?: return false
        alarms[id] = alarm.copy(enabled = enabled)
        observed.value = alarms.values.toList()
        return true
    }

    override suspend fun delete(id: String): Boolean {
        val removed = alarms.remove(id) != null
        observed.value = alarms.values.toList()
        return removed
    }

    override suspend fun hasStorageMigration(id: String): Boolean = false

    override suspend fun importLegacyIfNeeded(
        alarms: List<SavedAlarmSchedule>,
        migrationId: String,
        completedAtEpochMillis: Long,
    ): Boolean = false
}

private class FakeAndroidAlarmGateway(
    initial: Map<String, AlarmScheduleRequest> = emptyMap(),
) : AndroidAlarmGateway {
    val scheduled = initial.toMutableMap()
    val events = mutableListOf<String>()
    var scheduleFailure: Exception? = null

    override fun currentTimeMillis(): Long = 1_700_000_000_000L

    override fun schedule(
        request: AlarmScheduleRequest,
        triggerAtMillis: Long,
    ) {
        events += "schedule:${request.id}:${request.time}"
        scheduleFailure?.let { throw it }
        scheduled[request.id] = request
    }

    override fun cancel(alarmId: String) {
        events += "cancel:$alarmId"
        scheduled.remove(alarmId)
    }
}

private class RecordingAndroidAlarmAnalytics : AndroidAlarmCreationAnalytics {
    data class Event(
        val name: String,
        val request: AlarmScheduleRequest,
        val context: AlarmSettingsAnalyticsContext,
    )

    val events = mutableListOf<Event>()

    override fun recordAlarmCreated(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext,
    ) {
        events += Event("created", request, analyticsContext)
    }

    override fun recordAlarmUpdated(
        request: AlarmScheduleRequest,
        analyticsContext: AlarmSettingsAnalyticsContext,
    ) {
        events += Event("updated", request, analyticsContext)
    }
}
