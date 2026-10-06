package com.joon.ringout.presentation.roomcreate

import com.joon.ringout.presentation.common.WeekdayOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoomCreateViewModelTest {
    @Test
    fun `초기 상태는 매일 선택이며 진입 시각은 한 번만 저장한다`() {
        val viewModel = RoomCreateViewModel()

        assertEquals(1, viewModel.uiState.step)
        assertEquals(WeekdayOrder, viewModel.uiState.selectedDays)
        assertNull(viewModel.uiState.time24Hour)

        viewModel.initializeTimeIfNeeded("06:20")
        viewModel.initializeTimeIfNeeded("08:45")

        assertEquals("06:20", viewModel.uiState.time24Hour)
    }

    @Test
    fun `단계를 진행하면 누적 입력을 유지하고 검증된 초안을 만든다`() {
        val viewModel = RoomCreateViewModel()
        viewModel.initializeTimeIfNeeded("06:20")
        viewModel.updateName(" 가A2 ")

        assertTrue(viewModel.goToNextStep())
        assertEquals(2, viewModel.uiState.step)
        viewModel.updateIntroduction("함께 건강한 습관을 만들어요.")
        assertTrue(viewModel.goToNextStep())
        assertEquals(3, viewModel.uiState.step)

        viewModel.toggleDay("일")
        viewModel.toggleDay("월")
        viewModel.updateAmPm(isAm = false)
        viewModel.updateHour(12)
        viewModel.updateMinute(5)

        val draft = viewModel.createDraft()
        assertEquals("가A2", draft?.name)
        assertEquals("함께 건강한 습관을 만들어요.", draft?.introduction)
        assertEquals(listOf("화", "수", "목", "금", "토"), draft?.selectedDays)
        assertEquals("12:05", draft?.time24Hour)
    }

    @Test
    fun `이전 입력을 무효하게 수정하면 진행과 초안 생성이 막힌다`() {
        val viewModel = viewModelAtStepThree()

        viewModel.updateName("가")
        assertFalse(viewModel.uiState.canContinue)
        assertFalse(viewModel.goToNextStep())
        assertNull(viewModel.createDraft())

        viewModel.updateName("가A2")
        assertTrue(viewModel.uiState.canContinue)
        viewModel.updateIntroduction(" ")
        assertFalse(viewModel.uiState.canContinue)
        assertNull(viewModel.createDraft())

        viewModel.updateIntroduction("다시 입력한 소개")
        assertTrue(viewModel.uiState.canContinue)
    }

    @Test
    fun `요일은 월요일부터 중복 없이 정렬하고 전체 해제 시 진행을 막는다`() {
        val viewModel = viewModelAtStepThree()
        WeekdayOrder.forEach(viewModel::toggleDay)

        assertEquals(emptyList(), viewModel.uiState.selectedDays)
        assertFalse(viewModel.uiState.canContinue)

        viewModel.toggleDay("일")
        viewModel.toggleDay("수")
        viewModel.toggleDay("월")

        assertEquals(listOf("월", "수", "일"), viewModel.uiState.selectedDays)
        assertEquals(viewModel.uiState.selectedDays.size, viewModel.uiState.selectedDays.toSet().size)
        assertFalse(viewModel.uiState.selectedDays.contains("없는 요일"))
        assertTrue(viewModel.uiState.canContinue)
    }

    @Test
    fun `오전 열두 시는 자정으로 오후 열두 시는 정오로 변환한다`() {
        val viewModel = viewModelAtStepThree()

        viewModel.updateAmPm(isAm = true)
        viewModel.updateHour(12)
        viewModel.updateMinute(5)
        assertEquals("00:05", viewModel.createDraft()?.time24Hour)

        viewModel.updateAmPm(isAm = false)
        assertEquals("12:05", viewModel.createDraft()?.time24Hour)
    }

    @Test
    fun `어느 단계에서 뒤로 가도 입력 상태를 유지하고 경로를 닫는다`() {
        val viewModel = RoomCreateViewModel()
        var exitCount = 0

        val firstStepState = viewModel.uiState
        viewModel.onBack { exitCount += 1 }
        assertEquals(1, exitCount)
        assertEquals(firstStepState, viewModel.uiState)

        viewModel.initializeTimeIfNeeded("06:20")
        viewModel.updateName("가A2")
        assertTrue(viewModel.goToNextStep())
        val secondStepState = viewModel.uiState
        viewModel.onBack { exitCount += 1 }
        assertEquals(2, exitCount)
        assertEquals(secondStepState, viewModel.uiState)

        viewModel.updateIntroduction("소개")
        assertTrue(viewModel.goToNextStep())
        val thirdStepState = viewModel.uiState
        viewModel.onBack { exitCount += 1 }
        assertEquals(3, exitCount)
        assertEquals(thirdStepState, viewModel.uiState)
    }

    private fun viewModelAtStepThree(): RoomCreateViewModel = RoomCreateViewModel().also { viewModel ->
        viewModel.initializeTimeIfNeeded("06:20")
        viewModel.updateName("가A2")
        viewModel.goToNextStep()
        viewModel.updateIntroduction("소개")
        viewModel.goToNextStep()
    }
}
