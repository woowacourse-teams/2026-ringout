package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.GetMissionResultsByDate
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class MissionHistoryRepositoryTest {
    @Test
    fun repositoryMapsPayloadAndUseCaseReturnsUniqueSuccessDatesInMonth() {
        runBlocking {
            val repository = DefaultMissionHistoryRepository(
            InMemoryMissionHistoryDataSource(
                listOf(
                    MissionHistoryDto("SUCCESS", "2026-08-05"),
                    MissionHistoryDto("SUCCESS", "2026-08-05"),
                    MissionHistoryDto("FAILURE", "2026-08-06"),
                    MissionHistoryDto("SUCCESS", "2026-09-01"),
                ),
            ),
        )

            assertEquals(
                setOf(MissionDate.parse("2026-08-05")),
                GetMissionSuccessDates(repository)(MissionYearMonth(2026, 8)),
            )
            assertEquals(
                listOf(MissionResult.SUCCESS, MissionResult.SUCCESS, MissionResult.FAILURE),
                repository.getHistory(MissionYearMonth(2026, 8)).map { it.result },
            )
        }
    }

    @Test
    fun mappingRejectsUnknownResultAndInvalidDate() {
        runBlocking {
            assertFails {
                DefaultMissionHistoryRepository(
                    InMemoryMissionHistoryDataSource(
                        listOf(MissionHistoryDto("UNKNOWN", "2026-08-05")),
                    ),
                ).getHistory(MissionYearMonth(2026, 8))
            }
            assertFails {
                DefaultMissionHistoryRepository(
                    InMemoryMissionHistoryDataSource(
                        listOf(MissionHistoryDto("SUCCESS", "2026-02-30")),
                    ),
                ).getHistory(MissionYearMonth(2026, 2))
            }
        }
    }

    @Test
    fun recordingRequiresOccurrenceIdAndIgnoresDuplicateOccurrences() = runBlocking {
        val repository = DefaultMissionHistoryRepository(InMemoryMissionHistoryDataSource(emptyList()))
        val recordMissionResult = RecordMissionResult(repository)

        assertEquals(
            true,
            recordMissionResult(
                result = MissionResult.SUCCESS,
                completedAt = MissionDate.parse("2026-08-05"),
                occurrenceId = "occurrence-1",
            ),
        )
        assertEquals(
            false,
            recordMissionResult(
                result = MissionResult.FAILURE,
                completedAt = MissionDate.parse("2026-08-05"),
                occurrenceId = "occurrence-1",
            ),
        )
        assertFails {
            recordMissionResult(
                result = MissionResult.SUCCESS,
                completedAt = MissionDate.parse("2026-08-05"),
                occurrenceId = " ",
            )
        }
        Unit
    }

    @Test
    fun missionResultsByDateUsesLastHistoryEntryForEachDate() = runBlocking {
        val repository = DefaultMissionHistoryRepository(
            InMemoryMissionHistoryDataSource(
                listOf(
                    MissionHistoryDto("SUCCESS", "2026-08-05", "occurrence-1"),
                    MissionHistoryDto("FAILURE", "2026-08-05", "occurrence-2"),
                    MissionHistoryDto("SUCCESS", "2026-08-06", "occurrence-3"),
                ),
            ),
        )

        assertEquals(
            mapOf(
                MissionDate.parse("2026-08-05") to MissionResult.FAILURE,
                MissionDate.parse("2026-08-06") to MissionResult.SUCCESS,
            ),
            GetMissionResultsByDate(repository)(MissionYearMonth(2026, 8)),
        )
    }
}
