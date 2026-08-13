@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.joon.ringout.analytics

import com.joon.ringout.platform.IosAnalyticsEventDto
import com.joon.ringout.platform.IosAnalyticsTracker
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUUID
import kotlin.test.Test
import kotlin.test.assertEquals

class IosAlarmAnalyticsTest {
    @Test
    fun recordsAlarmCreationOnlyOnceWithAndroidCompatibleParameters() = withAnalytics { analytics, tracker ->
        analytics.recordAlarmCreated(
            alarmId = "alarm-1",
            repeatEnabled = true,
            repeatDayCount = 3,
        )
        analytics.recordAlarmCreated(
            alarmId = "alarm-1",
            repeatEnabled = true,
            repeatDayCount = 3,
        )

        val event = tracker.events.single()
        assertEquals("destination_alarm_created", event.name)
        assertEquals(1L, event.numberParameter("creation_index"))
        assertEquals(3L, event.numberParameter("repeat_day_count"))
        assertEquals("weekly", event.textParameter("schedule_type"))
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
