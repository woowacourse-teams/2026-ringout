@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.analytics

import com.joon.ringout.platform.IosAnalyticsEventDto
import com.joon.ringout.platform.IosAnalyticsTracker
import com.joon.ringout.alarm.AlarmScheduleRequest
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IosAlarmAnalyticsTest {
    @Test
    fun `모든 미션 결과는 시작 당시 전체 및 부분 확인 시각을 전송한다`() = withUsageStore { usage ->
        var now = 100L
        val tracker = RecordingIosAnalyticsTracker()
        val membership = RoomMembershipAnalytics(MemoryRoomAnalyticsStorage(), { "a" }, { now })
        membership.replace("a", 0, setOf(1))
        now = 200
        membership.update("a", 2, true)
        val analytics = IosAlarmAnalytics(tracker, usage, { now }, membership)
        listOf("completed", "expired", "force_ended").forEach { id ->
            analytics.recordMissionStarted(id, 0)
        }
        now = 300
        membership.update("a", 2, false)
        analytics.recordMissionCompleted("completed", 0, 200)
        analytics.recordMissionExpired("expired", 0, 200)
        analytics.recordMissionForceEnded("force_ended", 0, 200)
        assertEquals(6, tracker.events.size)
        tracker.events.forEach {
            assertEquals(100L, it.numberParameter("room_list_checked_at_ms"))
            assertEquals(200L, it.numberParameter("room_membership_observed_at_ms"))
        }
    }

    @Test
    fun `미션 재시도와 완료는 계정 변경 후에도 시작 당시 참여 정보를 전송한다`() = withUsageStore { usage ->
        val tracker = RecordingIosAnalyticsTracker()
        val storage = MemoryRoomAnalyticsStorage()
        val membership = RoomMembershipAnalytics(storage, { "a" }, { 0 })
        membership.replace("a", 0, setOf(1, 2))
        IosAlarmAnalytics(tracker, usage, { 0 }, membership).recordMissionStarted("root", 0)
        membership.update("a", 1, false)
        val recreated = IosAlarmAnalytics(tracker, usage, { 1000 },
            RoomMembershipAnalytics(storage, { "b" }, { 0 }))
        recreated.recordMissionStarted("root:retry-1", 1)
        recreated.recordMissionCompleted("root:retry-1", 1, 0)
        assertEquals(3, tracker.events.size)
        tracker.events.forEach {
            assertEquals("joined", it.textParameter("room_membership_state"))
            assertEquals(2L, it.numberParameter("joined_room_count"))
            assertEquals(0L, it.numberParameter("room_list_checked_at_ms"))
            assertEquals(0L, it.numberParameter("room_membership_observed_at_ms"))
            assertEquals(1L, it.numberParameter("use_index"))
        }
    }

    @Test
    fun `재설정은 생성 인덱스 없이 공통 설정값만 기록한다`() = withAnalytics { analytics, tracker ->
        analytics.recordAlarmUpdated(
            request = iosAlarmRequest("alarm-1").copy(
                time = "23:59",
                alarmSoundName = "private name",
                alarmSoundUri = "content://private/alarm",
            ),
            context = AlarmSettingsAnalyticsContext(),
        )

        val event = tracker.events.single()
        assertEquals("destination_alarm_updated", event.name)
        assertNull(event.numberParameter("creation_index"))
        assertEquals(2L, event.numberParameter("settings_schema_version"))
        assertEquals(12L, event.numberParameter("limit_minutes"))
        assertEquals("23:59", event.textParameter("alarm_time"))
        assertTrue(event.parameters.none { it.name.startsWith("alarm_sound_") })
        assertTrue(event.parameters.none { it.textValue == "private name" })
        assertTrue(event.parameters.none { it.textValue == "content://private/alarm" })
    }

    @Test
    fun `온보딩 이벤트 중복 방지 기록은 저장소 재생성 후에도 유지된다`() {
        val suiteName = "ringout-onboarding-test-${NSUUID().UUIDString}"
        val preferences = requireNotNull(NSUserDefaults(suiteName = suiteName))
        try {
            val first = IosAnalyticsUsageStore(preferences)
            assertTrue(first.claimOnboardingEvent(AnalyticsEventName.TutorialBegin))
            val recreated = IosAnalyticsUsageStore(requireNotNull(NSUserDefaults(suiteName = suiteName)))
            assertFalse(recreated.claimOnboardingEvent(AnalyticsEventName.TutorialBegin))
            assertTrue(recreated.claimOnboardingEvent(AnalyticsEventName.TutorialComplete))
            assertFalse(first.claimOnboardingEvent(AnalyticsEventName.TutorialComplete))
            assertEquals(1L, recreated.claimAlarmCreation("first"))
        } finally {
            preferences.removePersistentDomainForName(suiteName)
        }
    }

    @Test
    fun `알람 생성 이벤트는 반복 요일 수와 함께 한 번만 기록한다`() = withAnalytics { analytics, tracker ->
        analytics.recordAlarmCreated(
            request = iosAlarmRequest("alarm-1"),
            context = AlarmSettingsAnalyticsContext(),
        )
        analytics.recordAlarmCreated(
            request = iosAlarmRequest("alarm-1"),
            context = AlarmSettingsAnalyticsContext(),
        )

        val event = tracker.events.single()
        assertEquals("destination_alarm_created", event.name)
        assertEquals(1L, event.numberParameter("creation_index"))
        assertEquals(3L, event.numberParameter("repeat_day_count"))
        assertEquals("weekly", event.textParameter("schedule_type"))
        assertEquals(2L, event.numberParameter("settings_schema_version"))
        assertEquals(12L, event.numberParameter("limit_minutes"))
        assertEquals("06:20", event.textParameter("alarm_time"))
        assertEquals("mon,wed,fri", event.textParameter("repeat_days"))
        assertTrue(event.parameters.none { it.name.startsWith("alarm_sound_") })
    }

    @Test
    fun alarmAndDestinationCreationCountersAreIndependent() = withUsageStore { store ->
        assertEquals(1L, store.claimAlarmCreation("alarm-1"))
        assertEquals(1L, store.claimDestinationCreation("logged_in:1"))
        assertEquals(2L, store.claimAlarmCreation("alarm-2"))
        assertEquals(2L, store.claimDestinationCreation("logged_in:2"))
        assertNull(store.claimDestinationCreation("logged_in:1"))
    }

    @Test
    fun keepsUseIndexAcrossRetriesAndDeduplicatesTerminalEvents() = withAnalytics { analytics, tracker ->
        analytics.recordMissionStarted("root-occurrence", retryAttempt = 0)
        analytics.recordMissionStarted("root-occurrence:retry-1:id", retryAttempt = 1)
        analytics.recordMissionCompleted(
            occurrenceId = "root-occurrence:retry-1:id",
            retryAttempt = 1,
            startedAtEpochMillis = 1_000L,
        )
        analytics.recordMissionCompleted(
            occurrenceId = "root-occurrence:retry-1:id",
            retryAttempt = 1,
            startedAtEpochMillis = 1_000L,
        )

        val startedEvents = tracker.events.filter { it.name == "destination_mission_started" }
        assertEquals(2, startedEvents.size)
        assertEquals(listOf(1L, 1L), startedEvents.map { it.numberParameter("use_index") })
        assertEquals(
            1,
            tracker.events.count { it.name == "destination_mission_completed" },
        )
    }

    private fun withAnalytics(
        block: (IosAlarmAnalytics, RecordingIosAnalyticsTracker) -> Unit,
    ) {
        val suiteName = "ringout-analytics-test-${NSUUID().UUIDString}"
        val preferences = requireNotNull(NSUserDefaults(suiteName = suiteName))
        val tracker = RecordingIosAnalyticsTracker()
        try {
            block(
                IosAlarmAnalytics(
                    tracker = tracker,
                    usageStore = IosAnalyticsUsageStore(preferences),
                    nowEpochMillis = { 6L * 60_000L },
                ),
                tracker,
            )
        } finally {
            preferences.removePersistentDomainForName(suiteName)
        }
    }

    private fun withUsageStore(block: (IosAnalyticsUsageStore) -> Unit) {
        val suiteName = "ringout-analytics-test-${NSUUID().UUIDString}"
        val preferences = requireNotNull(NSUserDefaults(suiteName = suiteName))
        try {
            block(IosAnalyticsUsageStore(preferences))
        } finally {
            preferences.removePersistentDomainForName(suiteName)
        }
    }
}

private class RecordingIosAnalyticsTracker : IosAnalyticsTracker {
    val events = mutableListOf<IosAnalyticsEventDto>()

    override fun log(event: IosAnalyticsEventDto) {
        events += event
    }
}

private fun IosAnalyticsEventDto.numberParameter(name: String): Long? =
    parameters.singleOrNull { it.name == name }?.numberValue

private fun IosAnalyticsEventDto.textParameter(name: String): String? =
    parameters.singleOrNull { it.name == name }?.textValue

private fun iosAlarmRequest(id: String) = AlarmScheduleRequest(
    id = id,
    time = "06:20",
    selectedDays = listOf("월", "수", "금"),
    repeatEnabled = true,
    limitMinutes = 12,
    destinationName = "회사",
    destinationAddress = "서울특별시 중구 세종대로 110",
    destinationLatitude = 37.5665,
    destinationLongitude = 126.978,
    alarmSoundName = "기본 알람음",
    alarmSoundUri = null,
)
