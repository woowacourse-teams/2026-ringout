package com.joon.ringout.presentation.arrivalshare

import androidx.lifecycle.SavedStateHandle
import com.joon.ringout.domain.arrival.*
import com.joon.ringout.domain.missionhistory.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalShareViewModelTest {
    private val date = MissionDate.parse("2026-11-09")
    private val first = AlarmUsageRecord("occurrence:first", date, "first", alarmId = "alarm", ringingScheduledAtEpochMillis = 1000)
    private val success = AlarmUsageRecord("occurrence:A", date, "A", MissionResult.SUCCESS, alarmId = "alarm", missionCompletedAtEpochMillis = 2000)
    private val records = listOf(first, success)
    private val format: (Long) -> Pair<String, String> = { "2026.11.09" to if (it == 1000L) "06:30" else "06:53" }

    @Test fun `도착 직후에는 첫 울림과 선택한 성공 회차의 도착 시간을 표시한다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(records), format = format)
        runCurrent()
        assertEquals("06:30", vm.uiState.value.time)
        assertEquals("06:53", vm.uiState.value.arrivalTime)
        assertEquals("2026.11.09", vm.uiState.value.date)
    }

    @Test fun `도착 직후 울림 기록이 없으면 기상 시각은 비우고 도착 시각을 표시한다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(emptyList()), format = format)
        runCurrent()
        assertNull(vm.uiState.value.time)
        assertEquals("06:53", vm.uiState.value.arrivalTime)
    }

    @Test fun `기록 경로는 선택한 성공의 시각을 사용하고 데이터가 부족하면 오류를 보여준다`() = runTest {
        val group = records.groupByAlarm().single()
        val selection = ArrivalShareRecordSelection(date, group.key, success.key)
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(records), recordSelection = selection, format = format)
        runCurrent()
        assertEquals("06:30", vm.uiState.value.time)
        assertEquals("06:53", vm.uiState.value.arrivalTime)
        val missing = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(listOf(success)), recordSelection = selection, format = format)
        runCurrent()
        assertNotNull(missing.uiState.value.error)
    }

    @Test fun `이전 초안의 날짜와 시각은 현재 기록을 덮어쓰지 않고 사진과 설정은 복원한다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(records), format = format)
        vm.restoreDraft(ArrivalDraft("photo.png", false, true, "2025.01.01", "23:59", true))
        runCurrent()
        assertEquals("photo.png", vm.uiState.value.photoPath)
        assertFalse(vm.uiState.value.showTime)
        assertTrue(vm.uiState.value.useBlackText)
        assertEquals("2026.11.09", vm.uiState.value.date)
        assertEquals("06:30", vm.uiState.value.time)
        vm.restoreDraft(ArrivalDraft(photoPath = "stale.png"))
        assertEquals("photo.png", vm.uiState.value.photoPath)
    }

    @Test fun `사진 작업 중 중복 실행과 설정 변경을 막고 취소 후 편집을 유지한다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(records), format = format)
        runCurrent()
        vm.finishMedia(ArrivalMediaResult.Photo("photo.png"))
        assertTrue(vm.beginMedia()); assertFalse(vm.beginMedia())
        vm.setShowDate(false); vm.setShowTime(false); vm.toggleTextColor()
        assertTrue(vm.uiState.value.showDate); assertTrue(vm.uiState.value.showTime); assertFalse(vm.uiState.value.useBlackText)
        vm.finishMedia(ArrivalMediaResult.Done())
        assertEquals("photo.png", vm.uiState.value.photoPath)
        assertTrue(vm.beginMedia())
        vm.finishMedia(ArrivalMediaResult.Failed("권한 거절"))
        assertEquals("photo.png", vm.uiState.value.photoPath)
        assertEquals("권한 거절", vm.uiState.value.message)
        vm.setShowDate(false); vm.setShowTime(false); vm.toggleTextColor()
        assertFalse(vm.uiState.value.showDate); assertFalse(vm.uiState.value.showTime); assertTrue(vm.uiState.value.useBlackText)
    }

    @Test fun `글자 색상과 독립적인 시간 날짜 설정을 복원해도 도착을 확인 처리하지 않는다`() = runTest {
        val repository = EditorRepository()
        val saved = SavedStateHandle()
        val vm = ArrivalShareViewModel("A", repository, saved, backgroundScope, recordLookup = lookup(records), format = format)
        runCurrent()
        for (time in listOf(true, false)) for (dateVisible in listOf(true, false)) {
            vm.setShowTime(time); vm.setShowDate(dateVisible)
            assertEquals(time, vm.uiState.value.showTime)
            assertEquals(dateVisible, vm.uiState.value.showDate)
        }
        vm.toggleTextColor()
        val restored = ArrivalShareViewModel("A", repository, saved, backgroundScope, recordLookup = lookup(records), format = format)
        runCurrent()
        assertTrue(restored.uiState.value.useBlackText)
        assertFalse(restored.uiState.value.showTime)
        assertFalse(restored.uiState.value.showDate)
        assertEquals(0, repository.acknowledgements)
    }

    @Test fun `새 초안은 원본 시각이 같을 때 두 현지 시간 표시를 복원한다`() = runTest {
        val draft = ArrivalDraft(wakeEpochMillis = 1000, wakeLabel = "06:30", arrivalEpochMillis = 2000, arrivalLabel = "06:53")
        val changedTimezone: (Long) -> Pair<String, String> = { "2026.11.09" to "15:30" }
        val beforeLoad = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(records), format = changedTimezone)
        beforeLoad.restoreDraft(draft)
        runCurrent()
        assertEquals("06:30", beforeLoad.uiState.value.time)
        assertEquals("06:53", beforeLoad.uiState.value.arrivalTime)
        val afterLoad = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(records), format = changedTimezone)
        runCurrent()
        afterLoad.restoreDraft(draft)
        assertEquals("06:30", afterLoad.uiState.value.time)
        assertEquals("06:53", afterLoad.uiState.value.arrivalTime)
    }

    @Test fun `기록의 시각이 바뀌면 새 초안의 오래된 표시 시간은 사용하지 않는다`() = runTest {
        val vm = ArrivalShareViewModel("A", EditorRepository(), scope = backgroundScope, recordLookup = lookup(records), format = format)
        vm.restoreDraft(ArrivalDraft(wakeEpochMillis = 999, wakeLabel = "23:59", arrivalEpochMillis = 1999, arrivalLabel = "23:59"))
        runCurrent()
        assertEquals("06:30", vm.uiState.value.time)
        assertEquals("06:53", vm.uiState.value.arrivalTime)
    }

    @Test fun `저장된 상태의 시각 표시도 재생성 후 시간대 변경에 흔들리지 않는다`() = runTest {
        val saved = SavedStateHandle()
        val vm = ArrivalShareViewModel("A", EditorRepository(), saved, backgroundScope, recordLookup = lookup(records), format = format)
        runCurrent()
        val restored = ArrivalShareViewModel("A", EditorRepository(), saved, backgroundScope, recordLookup = lookup(records), format = { "2026.11.10" to "15:30" })
        runCurrent()
        assertEquals(vm.uiState.value.time, restored.uiState.value.time)
        assertEquals(vm.uiState.value.arrivalTime, restored.uiState.value.arrivalTime)
    }

    @Test fun `조회 오류를 재시도하고 저장된 표시 설정을 복원한다`() = runTest {
        val repository = EditorRepository().apply { fail = true }
        val saved = SavedStateHandle()
        val vm = ArrivalShareViewModel("A", repository, saved, backgroundScope, recordLookup = lookup(records), format = format)
        runCurrent(); assertNotNull(vm.uiState.value.error)
        repository.fail = false; vm.retry(); runCurrent(); assertNull(vm.uiState.value.error)
        vm.setShowTime(false); vm.setShowDate(false)
        val restored = ArrivalShareViewModel("A", repository, saved, backgroundScope, recordLookup = lookup(records), format = format)
        runCurrent(); assertFalse(restored.uiState.value.showTime); assertFalse(restored.uiState.value.showDate)
        repository.missing = true; restored.retry(); runCurrent(); assertNotNull(restored.uiState.value.error)
    }

    private fun lookup(records: List<AlarmUsageRecord>) = GetAlarmRecordGroup(EditorHistoryRepository(records))
}

private class EditorRepository : ArrivalResultRepository {
    var fail = false
    var missing = false
    var acknowledgements = 0
    override suspend fun getResult(occurrenceId: String): ArrivalResult? {
        if (fail) error("read failed")
        return if (missing) null else ArrivalResult("A", MissionDate.parse("2026-11-09"), 2000, 1500, 1, true, null)
    }
    override fun observeLatestUnacknowledgedResult() = flowOf<ArrivalResult?>(null)
    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) { acknowledgements++ }
    override suspend fun getGrowth() = ArrivalGrowth(1)
    override fun observeGrowth() = flowOf(ArrivalGrowth(1))
}

private class EditorHistoryRepository(private val records: List<AlarmUsageRecord>) : MissionHistoryRepository {
    override suspend fun getHistory(month: MissionYearMonth): List<MissionHistoryEntry> = error("Not used")
    override suspend fun record(entry: MissionHistoryEntry): Boolean = error("Not used")
    override suspend fun getLocalRecords(month: MissionYearMonth) = records.filter { it.date.belongsTo(month) }
    override suspend fun getLocalRecordsOnOccurrenceDate(occurrenceId: String): List<AlarmUsageRecord> =
        if (records.any { it.occurrenceId == occurrenceId }) records else emptyList()
}
