package com.joon.ringout.data.missionhistory

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionHistoryEntry
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LocalMissionHistoryRepositoryTest {
    @Test
    fun `로그인 상태에서도 기기 기록 조회는 서버를 호출하지 않고 로컬 결과를 반환한다`() = runTest {
        val remote = AuthenticatedRemoteHistory()
        val repository = DefaultMissionHistoryRepository(
            dataSource = InMemoryMissionHistoryDataSource(
                listOf(MissionHistoryDto("FAILURE", "2026-09-28", "local-occurrence")),
            ),
            remoteDataSource = remote,
        )

        val result = repository.getLocalHistory(MissionYearMonth(2026, 9))

        assertEquals(
            listOf(MissionHistoryEntry(MissionResult.FAILURE, MissionDate.parse("2026-09-28"), "local-occurrence")),
            result,
        )
        assertEquals(0, remote.historyQueries)
        assertEquals(0, remote.tokenQueries)

        assertEquals(MissionResult.SUCCESS, repository.getHistory(MissionYearMonth(2026, 9)).single().result)
        assertEquals(1, remote.historyQueries)
    }
}

private class AuthenticatedRemoteHistory : MissionHistoryRemoteDataSource {
    var historyQueries = 0
    var tokenQueries = 0

    override suspend fun hasAccessToken(): Boolean {
        tokenQueries++
        return true
    }

    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryDto> {
        historyQueries++
        return listOf(MissionHistoryDto("SUCCESS", "2026-09-28"))
    }

    override suspend fun recordSuccess(completedAt: MissionDate): Boolean = error("Not used")

    override suspend fun recordFailure(terminatedAt: MissionDate): Boolean = error("Not used")
}
