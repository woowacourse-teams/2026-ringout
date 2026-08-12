package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MissionHistoryMapperTest {
    @Test
    fun mapsExistingPersistedResultValuesToDomain() {
        assertEquals(
            MissionResult.SUCCESS,
            MissionHistoryDto("SUCCESS", "2026-08-05").toDomain().result,
        )
        assertEquals(
            MissionResult.FAILURE,
            MissionHistoryDto("FAILURE", "2026-08-05").toDomain().result,
        )
    }

    @Test
    fun mapsDomainResultsToStablePersistedValues() {
        val completedAt = MissionDate.parse("2026-08-05")

        assertEquals(
            "SUCCESS",
            MissionHistoryEntry(MissionResult.SUCCESS, completedAt).toDto().result,
        )
        assertEquals(
            "FAILURE",
            MissionHistoryEntry(MissionResult.FAILURE, completedAt).toDto().result,
        )
    }

    @Test
    fun rejectsUnsupportedPersistedResultValue() {
        assertFailsWith<IllegalStateException> {
            MissionHistoryDto("UNKNOWN", "2026-08-05").toDomain()
        }
    }
}
