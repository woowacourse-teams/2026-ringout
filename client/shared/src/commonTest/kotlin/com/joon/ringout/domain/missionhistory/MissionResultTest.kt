package com.joon.ringout.domain.missionhistory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MissionResultTest {
    @Test
    fun missionResultsUseStablePersistedValues() {
        assertEquals("SUCCESS", MissionResult.SUCCESS.persistedValue)
        assertEquals("FAILURE", MissionResult.FAILURE.persistedValue)
        assertEquals(MissionResult.SUCCESS, MissionResult.fromPersistedValue("SUCCESS"))
        assertEquals(MissionResult.FAILURE, MissionResult.fromPersistedValue("FAILURE"))
        assertNull(MissionResult.fromPersistedValue("UNKNOWN"))
    }
}
