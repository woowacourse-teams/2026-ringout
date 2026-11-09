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
    @Test fun `글자 색상을 왕복 전환하고 저장 중에는 고정하며 복원 후에도 유지한다`() = runTest {
        val saved = SavedStateHandle()
        val vm = ArrivalShareViewModel("A", EditorRepository(), saved, backgroundScope) { "2026.11.09" to "06:30" }
        runCurrent()
        assertFalse(vm.uiState.value.useBlackText)
        vm.toggleTextColor(); assertTrue(vm.uiState.value.useBlackText)
        assertTrue(vm.beginMedia())
        vm.toggleTextColor(); assertTrue(vm.uiState.value.useBlackText)
        vm.finishMedia(ArrivalMediaResult.Done())
        val restored = ArrivalShareViewModel("A", EditorRepository(), saved, backgroundScope)
        runCurrent(); assertTrue(restored.uiState.value.useBlackText)
        vm.toggleTextColor(); assertFalse(vm.uiState.value.useBlackText)
        val legacy = kotlinx.serialization.json.Json.decodeFromString<ArrivalDraft>("{}")
        assertFalse(legacy.useBlackText)
        restored.restoreDraft(legacy.copy(useBlackText = true))
        assertTrue(restored.uiState.value.useBlackText)
    }
    @Test fun `사진 취소와 실패는 기존 편집을 유지하고 중복 실행과 작업 중 토글을 막는다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope) { "2026.11.09" to "06:30" }
        runCurrent()
        vm.finishMedia(ArrivalMediaResult.Photo("photo-a.png"))
        assertTrue(vm.beginMedia()); assertFalse(vm.beginMedia())
        vm.setShowDate(false); vm.setShowTime(false)
        assertTrue(vm.uiState.value.showDate); assertTrue(vm.uiState.value.showTime)
        vm.finishMedia(ArrivalMediaResult.Done())
        assertEquals("photo-a.png", vm.uiState.value.photoPath)
        assertTrue(vm.beginMedia())
        vm.finishMedia(ArrivalMediaResult.Failed("권한 거절"))
        assertEquals("photo-a.png", vm.uiState.value.photoPath)
        assertEquals("권한 거절", vm.uiState.value.message)
        assertTrue(vm.beginMedia())
    }
    @Test fun `저장한 초안을 새 편집기에 복원해 파일 참조와 토글 및 현지 시각을 유지한다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope) { "2026.11.09" to "06:30" }
        val draft = ArrivalDraft("photo.png", false, true, "2026.11.08", "23:59")
        vm.restoreDraft(draft); runCurrent()
        assertEquals("photo.png", vm.uiState.value.photoPath)
        assertFalse(vm.uiState.value.showTime)
        assertEquals("2026.11.08", vm.uiState.value.date)
        vm.restoreDraft(draft.copy(photoPath = "stale.png"))
        assertEquals("photo.png", vm.uiState.value.photoPath)
    }
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
