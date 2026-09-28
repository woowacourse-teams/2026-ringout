package com.joon.ringout.presentation.ringing

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmRingingViewModelTest {
    @Test
    fun `자정을 지나면 현재 시각과 날짜를 함께 갱신한다`() = runTest {
        var now = AlarmRingingClockUiState("23:59", "2026년 9월 28일 월요일")
        val viewModel = AlarmRingingViewModel({ now }, backgroundScope)
        viewModel.startClock()
        runCurrent()
        assertEquals(now, viewModel.clock.value)

        now = AlarmRingingClockUiState("00:00", "2026년 9월 29일 화요일")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(now, viewModel.clock.value)
    }

    @Test
    fun `화면을 떠나면 갱신을 멈추고 다시 열면 즉시 현재 시각을 표시한다`() = runTest {
        val first = AlarmRingingClockUiState("07:00", "2026년 9월 28일 월요일")
        var now = first
        var reads = 0
        val viewModel = AlarmRingingViewModel(
            currentClock = { reads++; now },
            coroutineScope = backgroundScope,
        )
        viewModel.startClock()
        runCurrent()
        viewModel.stopClock()
        val readsBeforeLeaving = reads

        now = first.copy(time = "07:07")
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(readsBeforeLeaving, reads)
        assertEquals(first, viewModel.clock.value)

        viewModel.startClock()
        assertEquals(now, viewModel.clock.value)
    }

    @Test
    fun `기기 시각이 과거로 변경되어도 실제 현재 시각을 다시 읽는다`() = runTest {
        var now = AlarmRingingClockUiState("09:00", "2026년 9월 28일 월요일")
        val viewModel = AlarmRingingViewModel({ now }, backgroundScope)
        viewModel.startClock()
        runCurrent()

        now = AlarmRingingClockUiState("23:30", "2026년 9월 27일 일요일")
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(now, viewModel.clock.value)
    }
}
