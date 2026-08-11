package com.joon.ringout.analytics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmAnalyticsTest {
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
            alarmId = "private-alarm-id",
            repeatEnabled = true,
            repeatDayCount = 8,
        )
        analytics.recordMissionStarted(
            occurrenceId = "private-occurrence-id",
            retryAttempt = 0,
        )

        val approvedNames = setOf(
            AnalyticsParameterName.CreationIndex,
            AnalyticsParameterName.UseIndex,
            AnalyticsParameterName.RetryAttempt,
            AnalyticsParameterName.ScheduleType,
            AnalyticsParameterName.RepeatDayCount,
            AnalyticsParameterName.ElapsedBucket,
            AnalyticsParameterName.HoldDurationMillis,
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
            alarmId = "alarm-1",
            repeatEnabled = false,
            repeatDayCount = 3,
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
