package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
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
}
