package com.joon.ringout.presentation.arrivalshare
import androidx.lifecycle.SavedStateHandle
import com.joon.ringout.domain.arrival.*
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalShareViewModelTest {
    @Test fun `해제 시각의 날짜를 쓰고 토글 네 조합을 독립적으로 유지한다`() = runTest {
        val repo = EditorRepository()
        val vm = ArrivalShareViewModel("A", repo, scope = backgroundScope, format = { "2026.11.08" to "23:59" })
        runCurrent()
        assertEquals("2026.11.08", vm.uiState.value.date)
        assertEquals("23:59", vm.uiState.value.time)
        for (time in listOf(true, false)) for (date in listOf(true, false)) {
            vm.setShowTime(time); vm.setShowDate(date)
            assertEquals(time, vm.uiState.value.showTime); assertEquals(date, vm.uiState.value.showDate)
        }
        assertEquals(0, repo.acknowledgements)
    }
    @Test fun `해제 시각이 없으면 도착 날짜를 쓰며 시간 토글을 켜지 않는다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(dismissed = null), scope = backgroundScope)
        runCurrent(); vm.setShowTime(true)
        assertNull(vm.uiState.value.time)
        assertEquals("2026.11.09", vm.uiState.value.date)
    }
    @Test fun `편집 상태를 복원하면 토글과 처음 표시한 현지 시각을 유지한다`() = runTest {
        val saved = SavedStateHandle()
        val repo = EditorRepository()
        val vm = ArrivalShareViewModel("A", repo, saved, backgroundScope) { "2026.11.08" to "23:59" }
        runCurrent(); vm.setShowDate(false); vm.setShowTime(false)
        val restored = ArrivalShareViewModel("A", repo, saved, backgroundScope) { "2026.11.09" to "08:59" }
        runCurrent()
        assertEquals(vm.uiState.value, restored.uiState.value)
    }
    @Test fun `조회 실패를 재시도할 수 있고 없는 기록은 오류로 표시한다`() = runTest {
        val repo = EditorRepository().apply { fail = true }
        val vm = ArrivalShareViewModel("A", repo, scope = backgroundScope) { "2026.11.09" to "06:30" }
        runCurrent(); assertNotNull(vm.uiState.value.error)
        repo.fail = false; vm.retry(); runCurrent(); assertNull(vm.uiState.value.error)
        repo.missing = true; vm.retry(); runCurrent(); assertNotNull(vm.uiState.value.error)
    }
}
private class EditorRepository(val dismissed: Long? = 1000) : ArrivalResultRepository {
    var fail = false
    var missing = false
    var acknowledgements = 0
    override suspend fun getResult(occurrenceId: String): ArrivalResult? {
        if (fail) error("read failed")
        return if (missing) null else ArrivalResult("A", MissionDate.parse("2026-11-09"), 2000, dismissed, 1, true, null)
    }
    override fun observeLatestUnacknowledgedResult() = flowOf<ArrivalResult?>(null)
    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) { acknowledgements++ }
    override suspend fun getGrowth() = ArrivalGrowth(1)
    override fun observeGrowth() = flowOf(ArrivalGrowth(1))
}
