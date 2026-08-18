package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.GetMissionSuccessDates
import com.joon.ringout.domain.missionhistory.GetMissionResultsByDate
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.domain.missionhistory.RecordMissionResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MissionHistoryRepositoryTest {
    @Test
    fun `로그인 상태에서는 선택한 월을 서버에서 조회한다`() = runBlocking {
        val remoteDataSource = FakeMissionHistoryRemoteDataSource(
            hasAccessToken = true,
            history = listOf(MissionHistoryDto("SUCCESS", "2026-08-13")),
        )
        val repository = DefaultMissionHistoryRepository(
            dataSource = InMemoryMissionHistoryDataSource(emptyList()),
            remoteDataSource = remoteDataSource,
        )

        val history = repository.getHistory(MissionYearMonth(2026, 8))

        assertEquals(listOf(MissionDate.parse("2026-08-13")), history.map { it.completedAt })
        assertEquals(listOf(MissionYearMonth(2026, 8)), remoteDataSource.requestedMonths)
        assertTrue(remoteDataSource.getHistoryCalled)
    }

    @Test
    fun `비로그인 상태에서는 서버에 요청하지 않고 로컬 기록을 조회한다`() = runBlocking {
        val remoteDataSource = FakeMissionHistoryRemoteDataSource(
            hasAccessToken = false,
            history = listOf(MissionHistoryDto("SUCCESS", "2026-08-13")),
        )
        val repository = DefaultMissionHistoryRepository(
            dataSource = InMemoryMissionHistoryDataSource(
                listOf(MissionHistoryDto("SUCCESS", "2026-08-05")),
            ),
            remoteDataSource = remoteDataSource,
        )

        val history = repository.getHistory(MissionYearMonth(2026, 8))

        assertEquals(listOf(MissionDate.parse("2026-08-05")), history.map { it.completedAt })
        assertFalse(remoteDataSource.getHistoryCalled)
        assertEquals(emptyList(), remoteDataSource.requestedMonths)
    }

    @Test
    fun `로그인 상태에서 목적지 도착 성공 기록을 서버에 저장한다`() = runBlocking {
        val remoteDataSource = FakeMissionHistoryRemoteDataSource(hasAccessToken = true)
        val repository = DefaultMissionHistoryRepository(
            dataSource = InMemoryMissionHistoryDataSource(emptyList()),
            remoteDataSource = remoteDataSource,
        )
        val completedAt = MissionDate.parse("2026-08-13")

        val recorded = repository.record(
            MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = completedAt,
                occurrenceId = "logged-in-success",
            ),
        )

        assertTrue(recorded)
        assertEquals(listOf(completedAt), remoteDataSource.recordedSuccessDates)
    }

    @Test
    fun `비로그인 상태에서 목적지 도착 성공 기록은 서버에 보내지 않고 로컬에 저장한다`() = runBlocking {
        val remoteDataSource = FakeMissionHistoryRemoteDataSource(hasAccessToken = false)
        val repository = DefaultMissionHistoryRepository(
            dataSource = InMemoryMissionHistoryDataSource(emptyList()),
            remoteDataSource = remoteDataSource,
        )
        val completedAt = MissionDate.parse("2026-08-13")

        val recorded = repository.record(
            MissionHistoryEntry(
                result = MissionResult.SUCCESS,
                completedAt = completedAt,
                occurrenceId = "logged-out-success",
            ),
        )

        assertTrue(recorded)
        assertEquals(emptyList(), remoteDataSource.recordedSuccessDates)
        assertEquals(
            listOf(completedAt),
            repository.getHistory(MissionYearMonth(2026, 8)).map { it.completedAt },
        )
    }

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

    @Test
    fun successDatesKeepAStampWhenAnotherAttemptOnTheSameDateFails() = runBlocking {
        val repository = DefaultMissionHistoryRepository(
            InMemoryMissionHistoryDataSource(
                listOf(
                    MissionHistoryDto("SUCCESS", "2026-08-05", "occurrence-1"),
                    MissionHistoryDto("FAILURE", "2026-08-05", "occurrence-2"),
                    MissionHistoryDto("FAILURE", "2026-08-06", "occurrence-3"),
                ),
            ),
        )

        assertEquals(
            setOf(MissionDate.parse("2026-08-05")),
            GetMissionSuccessDates(repository)(MissionYearMonth(2026, 8)),
        )
    }
}

private class FakeMissionHistoryRemoteDataSource(
    private val hasAccessToken: Boolean,
    private val history: List<MissionHistoryDto> = emptyList(),
) : MissionHistoryRemoteDataSource {
    var getHistoryCalled = false
        private set
    val requestedMonths = mutableListOf<MissionYearMonth>()
    val recordedSuccessDates = mutableListOf<MissionDate>()

    override suspend fun hasAccessToken(): Boolean = hasAccessToken

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> {
        getHistoryCalled = true
        requestedMonths += month
        return history
    }

    override suspend fun recordSuccess(completedAt: MissionDate): Boolean {
        recordedSuccessDates += completedAt
        return true
    }

    override suspend fun recordFailure(terminatedAt: MissionDate): Boolean = true
}
