package com.joon.ringout.presentation.roomhome

import com.joon.ringout.domain.room.FakeRoomScheduleClock
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHomeCountdownTest {
    @Test
    fun `초기 샘플을 실제 계산으로 바꾸고 매초 갱신하다가 정각에 다음 일정으로 전환한다`() = runTest {
        val clock = FakeRoomScheduleClock(elapsedMillis = 6 * 3_600_000L - 2_000)
        val viewModel = RoomHomeViewModel(initialState, clock = clock, coroutineScope = backgroundScope)
        assertEquals("00:00:02", viewModel.uiState.value.remainingTimeText)
        viewModel.startCountdown()
        runCurrent()
        clock.elapsedMillis += 1_000
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("00:00:01", viewModel.uiState.value.remainingTimeText)
        clock.elapsedMillis += 1_000
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals("내일 오전 06:00", viewModel.uiState.value.nextScheduleText)
        assertEquals("1일 0시간", viewModel.uiState.value.remainingTimeText)
    }

    @Test
    fun `화면을 멈추면 시계를 읽지 않고 복귀 시 현재 시각으로 즉시 계산한다`() = runTest {
        val clock = FakeRoomScheduleClock(elapsedMillis = 5 * 3_600_000L)
        val viewModel = RoomHomeViewModel(initialState, clock = clock, coroutineScope = backgroundScope)
        viewModel.startCountdown()
        runCurrent()
        viewModel.stopCountdown()
        val reads = clock.reads
        clock.elapsedMillis += 5 * 86_400_000L + 30 * 60_000L
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(reads, clock.reads)
        assertEquals("01:00:00", viewModel.uiState.value.remainingTimeText)
        viewModel.startCountdown()
        assertEquals("00:30:00", viewModel.uiState.value.remainingTimeText)
    }

    @Test
    fun `반복 시작해도 갱신 작업은 하나이며 시각을 되돌리면 실제 남은 시간을 다시 계산한다`() = runTest {
        val clock = FakeRoomScheduleClock(elapsedMillis = 5 * 3_600_000L)
        val viewModel = RoomHomeViewModel(initialState, clock = clock, coroutineScope = backgroundScope)
        viewModel.startCountdown()
        viewModel.startCountdown()
        runCurrent()
        val reads = clock.reads
        clock.elapsedMillis -= 60_000
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(reads + 1, clock.reads)
        assertEquals("01:01:00", viewModel.uiState.value.remainingTimeText)
        assertNotEquals("00:59:59", viewModel.uiState.value.remainingTimeText)
    }

    private val initialState = RoomHomeUiState(
        room = RoomUiModel(
            id = "countdown", name = "아침 모임", description = "함께 달리는 모임", createdAt = "2026-09-01",
            activityDays = listOf("월", "화", "수", "목", "금", "토", "일"), activityTimeText = "오전 06:00",
            participantCount = 2, isJoined = true,
        ),
        nextScheduleText = "고정 샘플", remainingTimeText = "00:18:24",
    )
}
