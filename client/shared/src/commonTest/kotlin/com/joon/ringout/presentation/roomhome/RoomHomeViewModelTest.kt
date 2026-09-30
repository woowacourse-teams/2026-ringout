package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionYearMonth
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomHomeViewModelTest {
    @Test
    fun `처음 화면을 열면 날짜를 다시 선택하지 않아도 주입한 날짜의 기록과 달성 인원을 표시한다`() {
        val state = initialState().let { initial ->
            initial.copy(
                recordsState = initial.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
                ),
            )
        }

        val viewModel = RoomHomeViewModel(state, recordsByDate)

        val firstState = viewModel.uiState.value.recordsState
        assertEquals(decemberDate, firstState.selectedDate)
        assertEquals(decemberRecords.records, firstState.records)
        assertEquals(1, firstState.achievedMemberCount)
        assertEquals(mapOf(decemberDate to 1, januaryDate to 0), firstState.participantCounts)
    }

    @Test
    fun `날짜를 선택하면 해당 날짜의 회원 기록과 달성 인원을 표시한다`() {
        val viewModel = RoomHomeViewModel(initialState(), recordsByDate)

        viewModel.onDateSelected(januaryDate)

        val januaryState = viewModel.uiState.value.recordsState
        assertEquals(januaryDate, januaryState.selectedDate)
        assertEquals(januaryRecords.records, januaryState.records)
        assertEquals(januaryRecords.achievedMemberCount, januaryState.achievedMemberCount)
        assertEquals(mapOf(decemberDate to 1, januaryDate to 0), januaryState.participantCounts)

        viewModel.onDateSelected(decemberDate)

        assertEquals(decemberRecords.records, viewModel.uiState.value.recordsState.records)
        assertEquals(1, viewModel.uiState.value.recordsState.achievedMemberCount)
    }

    @Test
    fun `기록이 없는 날짜에는 이전 날짜의 기록과 달성 인원이 남지 않는다`() {
        val viewModel = RoomHomeViewModel(initialState(), recordsByDate)
        val emptyDate = MissionDate.parse("2027-01-07")

        viewModel.onDateSelected(emptyDate)

        val emptyState = viewModel.uiState.value.recordsState
        assertEquals(emptyDate, emptyState.selectedDate)
        assertTrue(emptyState.records.isEmpty())
        assertEquals(0, emptyState.achievedMemberCount)

        viewModel.onDateSelected(decemberDate)

        assertEquals(decemberRecords.records, viewModel.uiState.value.recordsState.records)
    }

    @Test
    fun `주 이동은 월과 연도 경계를 넘어 선택 날짜와 표시 주를 함께 변경한다`() {
        val viewModel = RoomHomeViewModel(initialState(), recordsByDate)

        viewModel.onPreviousWeek()

        assertEquals(MissionDate.parse("2026-12-23"), viewModel.uiState.value.recordsState.selectedDate)
        assertEquals(MissionDate.parse("2026-12-20"), viewModel.uiState.value.recordsState.visibleWeekStart)

        viewModel.onNextWeek()
        viewModel.onNextWeek()

        assertEquals(januaryDate, viewModel.uiState.value.recordsState.selectedDate)
        assertEquals(MissionDate.parse("2027-01-03"), viewModel.uiState.value.recordsState.visibleWeekStart)
        assertEquals(MissionYearMonth(2027, 1), viewModel.uiState.value.calendarMonth)
        assertEquals(januaryRecords.records, viewModel.uiState.value.recordsState.records)

        viewModel.onPreviousWeek()

        assertEquals(decemberDate, viewModel.uiState.value.recordsState.selectedDate)
        assertEquals(MissionYearMonth(2026, 12), viewModel.uiState.value.calendarMonth)
    }

    @Test
    fun `정보와 기록 탭을 전환해도 선택 날짜와 회원 기록을 유지하고 달력은 닫는다`() {
        val viewModel = RoomHomeViewModel(initialState(), recordsByDate)
        viewModel.onDateSelected(januaryDate)
        viewModel.onTabSelected(RoomHomeTab.Records)
        viewModel.onOpenCalendar()

        viewModel.onTabSelected(RoomHomeTab.Info)

        assertEquals(RoomHomeTab.Info, viewModel.uiState.value.selectedTab)
        assertFalse(viewModel.uiState.value.isCalendarVisible)
        assertEquals(januaryDate, viewModel.uiState.value.recordsState.selectedDate)

        viewModel.onTabSelected(RoomHomeTab.Records)

        assertEquals(RoomHomeTab.Records, viewModel.uiState.value.selectedTab)
        assertEquals(januaryDate, viewModel.uiState.value.recordsState.selectedDate)
        assertEquals(januaryRecords.records, viewModel.uiState.value.recordsState.records)
    }

    @Test
    fun `달력 월 이동과 취소는 선택 날짜를 유지하고 날짜 선택은 목록을 바꾸며 달력을 닫는다`() {
        val viewModel = RoomHomeViewModel(initialState(), recordsByDate)

        viewModel.onOpenCalendar()
        assertTrue(viewModel.uiState.value.isCalendarVisible)
        assertEquals(MissionYearMonth(2026, 12), viewModel.uiState.value.calendarMonth)

        viewModel.onNextMonth()
        assertEquals(MissionYearMonth(2027, 1), viewModel.uiState.value.calendarMonth)
        assertEquals(decemberDate, viewModel.uiState.value.recordsState.selectedDate)

        viewModel.onPreviousMonth()
        assertEquals(MissionYearMonth(2026, 12), viewModel.uiState.value.calendarMonth)
        viewModel.onNextMonth()
        viewModel.onDismissCalendar()
        assertFalse(viewModel.uiState.value.isCalendarVisible)
        assertEquals(decemberDate, viewModel.uiState.value.recordsState.selectedDate)

        viewModel.onOpenCalendar()
        assertEquals(MissionYearMonth(2026, 12), viewModel.uiState.value.calendarMonth)
        viewModel.onDateSelected(januaryDate)

        assertFalse(viewModel.uiState.value.isCalendarVisible)
        assertEquals(MissionYearMonth(2027, 1), viewModel.uiState.value.calendarMonth)
        assertEquals(januaryDate, viewModel.uiState.value.recordsState.selectedDate)
        assertEquals(januaryRecords.records, viewModel.uiState.value.recordsState.records)
    }

    @Test
    fun `새로고침은 선택 날짜와 탭을 유지하며 그 날짜의 기록으로 오류 상태를 갱신한다`() {
        val state = initialState().copy(
            selectedTab = RoomHomeTab.Records,
            recordsState = RoomHomeRecordsUiState(
                selectedDate = januaryDate,
                records = decemberRecords.records,
                achievedMemberCount = 1,
                isLoading = true,
                errorMessage = "이전 표시 오류",
            ),
        )
        val viewModel = RoomHomeViewModel(state, recordsByDate)

        viewModel.onRefresh()

        val refreshedState = viewModel.uiState.value
        assertEquals(RoomHomeTab.Records, refreshedState.selectedTab)
        assertEquals(januaryDate, refreshedState.recordsState.selectedDate)
        assertEquals(januaryRecords.records, refreshedState.recordsState.records)
        assertEquals(0, refreshedState.recordsState.achievedMemberCount)
        assertFalse(refreshedState.recordsState.isLoading)
        assertNull(refreshedState.recordsState.errorMessage)
    }

    @Test
    fun `비가입자나 기록 권한이 없는 회원에게는 초기 표시와 날짜 변경 후에도 기록과 참여 인원을 숨긴다`() {
        val restrictedStates = listOf(
            initialState().copy(room = room.copy(isJoined = false)),
            initialState().let { state ->
                state.copy(recordsState = state.recordsState.copy(canViewRecords = false))
            },
        )

        restrictedStates.forEach { state ->
            val viewModel = RoomHomeViewModel(state, recordsByDate)
            assertHiddenRecords(viewModel.uiState.value.recordsState)

            viewModel.onDateSelected(januaryDate)
            assertHiddenRecords(viewModel.uiState.value.recordsState)

            viewModel.onRefresh()
            assertHiddenRecords(viewModel.uiState.value.recordsState)
        }
    }

    private fun assertHiddenRecords(state: RoomHomeRecordsUiState) {
        assertFalse(state.canViewRecords)
        assertTrue(state.records.isEmpty())
        assertEquals(0, state.achievedMemberCount)
        assertTrue(state.participantCounts.isEmpty())
    }

    private fun initialState() = RoomHomeUiState(
        room = room,
        recordsState = RoomHomeRecordsUiState(
            selectedDate = decemberDate,
            records = decemberRecords.records,
            achievedMemberCount = decemberRecords.achievedMemberCount,
            participantCounts = mapOf(decemberDate to 1, januaryDate to 0),
        ),
    )

    private companion object {
        val decemberDate = MissionDate.parse("2026-12-30")
        val januaryDate = MissionDate.parse("2027-01-06")
        val room = RoomUiModel(
            id = "test-room",
            name = "아침 모임",
            description = "회원끼리 아침 목표를 함께 실천하는 모임",
            createdAt = "2026-09-01",
            activityDays = listOf("월", "수", "금"),
            activityTimeText = "오전 06:00",
            participantCount = 2,
            isJoined = true,
        )
        val decemberRecords = RoomHomeDayRecordsUiModel(
            records = listOf(
                RoomHomeRecordUiModel(
                    id = "december-arrival",
                    memberId = "member-1",
                    nickname = "첫 번째 회원",
                    timeText = "06:30",
                    event = RoomHomeRecordEvent.Arrived,
                ),
            ),
            achievedMemberCount = 1,
        )
        val januaryRecords = RoomHomeDayRecordsUiModel(
            records = listOf(
                RoomHomeRecordUiModel(
                    id = "january-moving",
                    memberId = "member-2",
                    nickname = "두 번째 회원",
                    timeText = "06:10",
                    event = RoomHomeRecordEvent.Moving,
                ),
                RoomHomeRecordUiModel(
                    id = "january-ringing",
                    memberId = "member-1",
                    nickname = "첫 번째 회원",
                    timeText = "06:00",
                    event = RoomHomeRecordEvent.Ringing,
                    ringCount = 2,
                ),
            ),
            achievedMemberCount = 0,
        )
        val recordsByDate = mapOf(decemberDate to decemberRecords, januaryDate to januaryRecords)
    }
}
