package com.joon.ringout.analytics

import com.joon.ringout.alarm.AlarmScheduleRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmAnalyticsTest {
    @Test
    fun `생성과 재설정은 동일한 설정 계약을 사용하고 재설정에는 생성 인덱스가 없다`() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 0L },
        )
        val request = analyticsAlarmRequest(
            id = "private-alarm-id",
            selectedDays = listOf("월", "수", "금"),
        ).copy(
            time = "06:20",
            alarmSoundName = "Private sound name",
            alarmSoundUri = "content://private/alarm",
        )
        val context = AlarmSettingsAnalyticsContext(
            soundDisplaySelection = AlarmSoundDisplaySelection(
                surface = AnalyticsAlarmSoundSurface.OnboardingStep,
                position = 3,
                listSize = 5,
                selectionChanged = true,
            ),
        )

        analytics.recordAlarmCreated(request, context)
        analytics.recordAlarmUpdated(request, context)

        val created = tracker.events[0]
        val updated = tracker.events[1]
        assertEquals(
            listOf(
                AnalyticsEventName.DestinationAlarmCreated,
                AnalyticsEventName.DestinationAlarmUpdated,
            ),
            tracker.events.map(AnalyticsEvent::name),
        )
        assertEquals(1L, created.number(AnalyticsParameterName.CreationIndex))
        assertFalse(updated.parameters.containsKey(AnalyticsParameterName.CreationIndex))
        listOf(created, updated).forEach { event ->
            assertEquals(2L, event.number(AnalyticsParameterName.SettingsSchemaVersion))
            assertEquals(12L, event.number(AnalyticsParameterName.LimitMinutes))
            assertEquals("06:20", event.text(AnalyticsParameterName.AlarmTime))
            assertEquals("mon,wed,fri", event.text(AnalyticsParameterName.RepeatDays))
            assertEquals("weekly", event.text(AnalyticsParameterName.ScheduleType))
            assertEquals(3L, event.number(AnalyticsParameterName.RepeatDayCount))
            assertEquals("device_alarm", event.text(AnalyticsParameterName.AlarmSoundSource))
            assertEquals(1L, event.number(AnalyticsParameterName.AlarmSoundListConfirmed))
            assertEquals("onboarding_step", event.text(AnalyticsParameterName.AlarmSoundSurface))
            assertEquals(3L, event.number(AnalyticsParameterName.AlarmSoundPosition))
            assertEquals(5L, event.number(AnalyticsParameterName.AlarmSoundListSize))
            assertEquals(1L, event.number(AnalyticsParameterName.AlarmSoundSelectionChanged))
        }
        val serializedValues = tracker.events
            .flatMap { event -> event.parameters.values }
            .joinToString()
        assertFalse(serializedValues.contains("private-alarm-id"))
        assertFalse(serializedValues.contains("Private sound name"))
        assertFalse(serializedValues.contains("content://private/alarm"))
    }

    @Test
    fun `목록 미확인 저장은 기본 음원과 확인 안 함만 기록한다`() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 0L },
        )

        analytics.recordAlarmUpdated(
            request = analyticsAlarmRequest(id = "alarm-1"),
            context = AlarmSettingsAnalyticsContext(),
        )

        val event = tracker.events.single()
        assertEquals("system_default", event.text(AnalyticsParameterName.AlarmSoundSource))
        assertEquals(0L, event.number(AnalyticsParameterName.AlarmSoundListConfirmed))
        assertFalse(event.parameters.containsKey(AnalyticsParameterName.AlarmSoundSurface))
        assertFalse(event.parameters.containsKey(AnalyticsParameterName.AlarmSoundPosition))
        assertFalse(event.parameters.containsKey(AnalyticsParameterName.AlarmSoundListSize))
        assertFalse(event.parameters.containsKey(AnalyticsParameterName.AlarmSoundSelectionChanged))
    }

    @Test
    fun `잘못된 시간이나 제한 시간은 설정 이벤트를 만들지 않는다`() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 0L },
        )

        analytics.recordAlarmUpdated(
            request = analyticsAlarmRequest(id = "invalid-time").copy(time = "6:20"),
            context = AlarmSettingsAnalyticsContext(),
        )
        analytics.recordAlarmUpdated(
            request = analyticsAlarmRequest(id = "invalid-limit").copy(limitMinutes = 31),
            context = AlarmSettingsAnalyticsContext(),
        )

        assertTrue(tracker.events.isEmpty())
    }

    @Test
    fun `온보딩 이벤트 중복 방지 기록은 저장소 재생성 후에도 유지되고 알람 생성과 독립적이다`() {
        val preferences = InMemoryAnalyticsUsagePreferences()
        val first = AnalyticsUsageStore(preferences)
        assertTrue(first.claimOnboardingEvent(AnalyticsEventName.TutorialBegin))
        val recreated = AnalyticsUsageStore(preferences)
        assertFalse(recreated.claimOnboardingEvent(AnalyticsEventName.TutorialBegin))
        assertTrue(recreated.claimOnboardingEvent(AnalyticsEventName.TutorialComplete))
        assertFalse(first.claimOnboardingEvent(AnalyticsEventName.TutorialComplete))
        assertEquals(1L, recreated.claimAlarmCreation("first"))
    }

    @Test
    fun `온보딩 이벤트 중복 방지 기록 저장에 실패하면 성공을 반환하지 않는다`() {
        val store = AnalyticsUsageStore(object : AnalyticsUsagePreferences {
            override fun contains(key: String): Boolean = false
            override fun getLong(key: String, defaultValue: Long): Long = defaultValue
            override fun commit(longValues: Map<String, Long>, trueFlags: Set<String>): Boolean = false
        })
        assertFalse(store.claimOnboardingEvent(AnalyticsEventName.TutorialBegin))
        assertFalse(store.claimOnboardingEvent(AnalyticsEventName.TutorialComplete))
    }

    @Test
    fun forceEndHoldAttemptAllowsOneTerminalEventBeforeTheNextAttempt() {
        val store = ForceEndHoldAnalyticsAttemptStore()
        val attempt = ForceEndHoldAnalyticsAttempt(
            occurrenceId = "alarm-1:1000",
            retryAttempt = 1,
        )

        assertTrue(store.begin(attempt))
        assertFalse(store.begin(attempt))
        assertEquals(attempt, store.finish(attempt.occurrenceId))
        assertNull(store.finish(attempt.occurrenceId))
        assertTrue(store.begin(attempt))
    }

    @Test
    fun retryOccurrencesReuseTheOriginalUseIndex() {
        val store = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences())

        assertEquals(1L, store.getOrCreateUseIndex("alarm-1:1000"))
        assertEquals(1L, store.getOrCreateUseIndex("alarm-1:1000:retry-1"))
        assertEquals(1L, store.getOrCreateUseIndex("alarm-1:1000:retry-1:retry-2"))
        assertEquals(2L, store.getOrCreateUseIndex("alarm-1:2000"))
    }

    @Test
    fun alarmCreationIsClaimedOncePerAlarm() {
        val store = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences())

        assertEquals(1L, store.claimAlarmCreation("alarm-1"))
        assertNull(store.claimAlarmCreation("alarm-1"))
        assertEquals(2L, store.claimAlarmCreation("alarm-2"))
    }

    @Test
    fun alarmAndDestinationCreationCountersAreIndependent() {
        val store = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences())

        assertEquals(1L, store.claimAlarmCreation("alarm-1"))
        assertEquals(1L, store.claimDestinationCreation("logged_in:1"))
        assertEquals(2L, store.claimAlarmCreation("alarm-2"))
        assertEquals(2L, store.claimDestinationCreation("logged_in:2"))
        assertNull(store.claimDestinationCreation("logged_in:1"))
    }

    @Test
    fun eventClaimsAreScopedToEventAndOccurrence() {
        val store = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences())

        assertTrue(
            store.claimEvent(
                AnalyticsEventName.DestinationMissionStarted,
                "alarm-1:1000",
            ),
        )
        assertFalse(
            store.claimEvent(
                AnalyticsEventName.DestinationMissionStarted,
                "alarm-1:1000",
            ),
        )
        assertTrue(
            store.claimEvent(
                AnalyticsEventName.DestinationMissionCompleted,
                "alarm-1:1000",
            ),
        )
        assertTrue(
            store.claimEvent(
                AnalyticsEventName.DestinationMissionStarted,
                "alarm-1:1000:retry-1",
            ),
        )
    }

    @Test
    fun missionEventsKeepOneUseIndexAcrossRetries() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 20L * 60_000L },
        )

        analytics.recordMissionStarted(
            occurrenceId = "alarm-1:1000",
            retryAttempt = 0,
        )
        analytics.recordMissionStarted(
            occurrenceId = "alarm-1:1000",
            retryAttempt = 0,
        )
        analytics.recordMissionStarted(
            occurrenceId = "alarm-1:1000:retry-1",
            retryAttempt = 1,
        )
        analytics.recordMissionCompleted(
            occurrenceId = "alarm-1:1000:retry-1",
            retryAttempt = 1,
            startedAtEpochMillis = 10L * 60_000L,
        )

        assertEquals(
            listOf(
                AnalyticsEventName.DestinationMissionStarted,
                AnalyticsEventName.DestinationMissionStarted,
                AnalyticsEventName.DestinationMissionCompleted,
            ),
            tracker.events.map(AnalyticsEvent::name),
        )
        assertEquals(
            listOf(1L, 1L, 1L),
            tracker.events.map { event -> event.number(AnalyticsParameterName.UseIndex) },
        )
        assertEquals(
            "5_to_15m",
            tracker.events.last().text(AnalyticsParameterName.ElapsedBucket),
        )
    }

    @Test
    fun outcomeWithoutMissionStartDoesNotCreateAUseIndex() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 60_000L },
        )

        analytics.recordMissionForceEnded(
            occurrenceId = "alarm-1:1000",
            retryAttempt = 0,
            startedAtEpochMillis = 0L,
        )
        analytics.recordForceEndHoldStarted(
            occurrenceId = "alarm-1:1000",
            retryAttempt = 0,
        )

        assertTrue(tracker.events.isEmpty())
    }

    @Test
    fun forceEndHoldAttemptsRecordEveryStartAndOneTerminalEvent() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 0L },
        )
        val occurrenceId = "alarm-1:1000"
        analytics.recordMissionStarted(
            occurrenceId = occurrenceId,
            retryAttempt = 2,
        )

        analytics.recordForceEndHoldStarted(
            occurrenceId = occurrenceId,
            retryAttempt = 2,
        )
        analytics.recordForceEndHoldCancelled(
            occurrenceId = occurrenceId,
            retryAttempt = 2,
            holdDurationMillis = 1_234L,
        )
        analytics.recordForceEndHoldStarted(
            occurrenceId = occurrenceId,
            retryAttempt = 2,
        )
        analytics.recordForceEndHoldCompleted(
            occurrenceId = occurrenceId,
            retryAttempt = 2,
            holdDurationMillis = 30_015L,
        )

        assertEquals(
            listOf(
                AnalyticsEventName.ForceEndHoldStarted,
                AnalyticsEventName.ForceEndHoldCancelled,
                AnalyticsEventName.ForceEndHoldStarted,
                AnalyticsEventName.ForceEndHoldCompleted,
            ),
            tracker.events.drop(1).map(AnalyticsEvent::name),
        )
        val commonParameterNames = setOf(
            AnalyticsParameterName.UseIndex,
            AnalyticsParameterName.RetryAttempt,
        )
        assertEquals(
            commonParameterNames,
            tracker.events[1].parameters.keys,
        )
        assertEquals(
            commonParameterNames + AnalyticsParameterName.HoldDurationMillis,
            tracker.events[2].parameters.keys,
        )
        assertEquals(
            commonParameterNames + AnalyticsParameterName.HoldDurationMillis,
            tracker.events[4].parameters.keys,
        )
        assertEquals(
            1_234L,
            tracker.events[2].number(AnalyticsParameterName.HoldDurationMillis),
        )
        assertEquals(
            30_015L,
            tracker.events[4].number(AnalyticsParameterName.HoldDurationMillis),
        )
        assertTrue(
            tracker.events.drop(1).all { event ->
                event.number(AnalyticsParameterName.UseIndex) == 1L &&
                    event.number(AnalyticsParameterName.RetryAttempt) == 2L
            },
        )
    }

    @Test
    fun payloadUsesOnlyTheApprovedParameterNames() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 0L },
        )

        analytics.recordAlarmCreated(
            request = analyticsAlarmRequest(
                id = "private-alarm-id",
                selectedDays = listOf("월", "화", "수", "목", "금", "토", "일"),
            ),
            context = AlarmSettingsAnalyticsContext(),
        )
        analytics.recordMissionStarted(
            occurrenceId = "private-occurrence-id",
            retryAttempt = 0,
        )

        val approvedNames = setOf(
            AnalyticsParameterName.CreationIndex,
            AnalyticsParameterName.SettingsSchemaVersion,
            AnalyticsParameterName.LimitMinutes,
            AnalyticsParameterName.AlarmTime,
            AnalyticsParameterName.RepeatDays,
            AnalyticsParameterName.UseIndex,
            AnalyticsParameterName.RetryAttempt,
            AnalyticsParameterName.ScheduleType,
            AnalyticsParameterName.RepeatDayCount,
            AnalyticsParameterName.ElapsedBucket,
            AnalyticsParameterName.HoldDurationMillis,
            AnalyticsParameterName.AlarmSoundSource,
            AnalyticsParameterName.AlarmSoundListConfirmed,
        )
        assertTrue(
            tracker.events.all { event -> event.parameters.keys.all(approvedNames::contains) },
        )
        assertEquals(
            7L,
            tracker.events.first().number(AnalyticsParameterName.RepeatDayCount),
        )
        val serializedValues = tracker.events
            .flatMap { event -> event.parameters.values }
            .joinToString()
        assertFalse(serializedValues.contains("private-alarm-id"))
        assertFalse(serializedValues.contains("private-occurrence-id"))
    }

    @Test
    fun oneTimeAlarmDoesNotReportStaleRepeatDays() {
        val tracker = RecordingAnalyticsTracker()
        val analytics = AlarmAnalytics(
            tracker = tracker,
            usageStore = AnalyticsUsageStore(InMemoryAnalyticsUsagePreferences()),
            nowEpochMillis = { 0L },
        )

        analytics.recordAlarmCreated(
            request = analyticsAlarmRequest(
                id = "alarm-1",
                repeatEnabled = false,
                selectedDays = listOf("월", "화", "수"),
            ),
            context = AlarmSettingsAnalyticsContext(),
        )

        assertEquals(
            "once",
            tracker.events.single().text(AnalyticsParameterName.ScheduleType),
        )
        assertEquals(
            0L,
            tracker.events.single().number(AnalyticsParameterName.RepeatDayCount),
        )
    }

    @Test
    fun elapsedTimeUsesStableBuckets() {
        assertEquals("under_5m", analyticsElapsedBucket(5L * 60_000L - 1L))
        assertEquals("5_to_15m", analyticsElapsedBucket(5L * 60_000L))
        assertEquals("15_to_30m", analyticsElapsedBucket(15L * 60_000L))
        assertEquals("over_30m", analyticsElapsedBucket(30L * 60_000L))
    }
}

private class RecordingAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }
}

private class InMemoryAnalyticsUsagePreferences : AnalyticsUsagePreferences {
    private val longValues = mutableMapOf<String, Long>()
    private val flags = mutableSetOf<String>()

    override fun contains(key: String): Boolean = key in longValues || key in flags

    override fun getLong(key: String, defaultValue: Long): Long =
        longValues[key] ?: defaultValue

    override fun commit(
        longValues: Map<String, Long>,
        trueFlags: Set<String>,
    ): Boolean {
        this.longValues += longValues
        flags += trueFlags
        return true
    }
}

private fun AnalyticsEvent.number(name: AnalyticsParameterName): Long =
    (parameters.getValue(name) as AnalyticsParameterValue.Number).value

private fun AnalyticsEvent.text(name: AnalyticsParameterName): String =
    (parameters.getValue(name) as AnalyticsParameterValue.Text).value

private fun analyticsAlarmRequest(
    id: String,
    repeatEnabled: Boolean = true,
    selectedDays: List<String> = listOf("월", "금"),
) = AlarmScheduleRequest(
    id = id,
    time = "06:20",
    selectedDays = selectedDays,
    repeatEnabled = repeatEnabled,
    limitMinutes = 12,
    destinationName = "회사",
    destinationAddress = "서울특별시 중구 세종대로 110",
    destinationLatitude = 37.5665,
    destinationLongitude = 126.978,
    alarmSoundName = "기본 알람음",
    alarmSoundUri = null,
)
