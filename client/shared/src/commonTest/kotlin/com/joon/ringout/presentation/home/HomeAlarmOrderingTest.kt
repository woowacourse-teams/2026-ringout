package com.joon.ringout.presentation.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeAlarmOrderingTest {
    @Test
    fun placesEnabledAlarmsFirstWhilePreservingEachGroupsOrder() {
        val alarms = listOf(
            alarm(id = "disabled-1", isEnabled = false),
            alarm(id = "enabled-1", isEnabled = true),
            alarm(id = "disabled-2", isEnabled = false),
            alarm(id = "enabled-2", isEnabled = true),
        )

        val orderedIds = enabledAlarmsFirst(alarms).map(HomeAlarm::id)

        assertEquals(
            listOf("enabled-1", "enabled-2", "disabled-1", "disabled-2"),
            orderedIds,
        )
    }

    @Test
    fun preservesOrderWhenAllAlarmsHaveTheSameState() {
        val alarms = listOf(
            alarm(id = "first", isEnabled = true),
            alarm(id = "second", isEnabled = true),
            alarm(id = "third", isEnabled = true),
        )

        assertEquals(
            listOf("first", "second", "third"),
            enabledAlarmsFirst(alarms).map(HomeAlarm::id),
        )
    }

    private fun alarm(
        id: String,
        isEnabled: Boolean,
    ) = HomeAlarm(
        id = id,
        time = "07:00",
        days = "매일",
        destination = "목적지",
        timeLimitMinutes = 10,
        isEnabled = isEnabled,
    )
}
