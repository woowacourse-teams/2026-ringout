package com.joon.ringout.presentation.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStateTest {
    @Test
    fun `서비스 소개부터 출발 안내까지 네 페이지를 순서대로 진행한다`() {
        val flow = OnboardingViewModel()
        val expectedSteps = listOf(
            OnboardingStep.Introduction,
            OnboardingStep.Destination,
            OnboardingStep.Interval,
            OnboardingStep.Departure,
        )
        assertEquals(expectedSteps, flow.uiState.value.steps)

        expectedSteps.forEachIndexed { index, step ->
            assertEquals(step, flow.uiState.value.step)
            assertEquals(index, flow.uiState.value.currentStepIndex)
            assertEquals(index == expectedSteps.lastIndex, flow.uiState.value.isLastStep)
            assertFalse(flow.uiState.value.isCompletionRequested)
            assertEquals(
                if (index == expectedSteps.lastIndex) OnboardingAdvance.Complete else OnboardingAdvance.None,
                flow.requestNext(),
            )
        }
        assertTrue(flow.uiState.value.isCompletionRequested)
    }

    @Test
    fun `스와이프로 선택한 페이지에서 다음 버튼과 뒤로 가기를 이어서 사용할 수 있다`() {
        val flow = OnboardingViewModel()
        flow.onPageChanged(2)
        assertEquals(OnboardingStep.Interval, flow.uiState.value.step)
        flow.back()
        assertEquals(OnboardingStep.Destination, flow.uiState.value.step)
        assertEquals(OnboardingAdvance.None, flow.requestNext())
        assertEquals(OnboardingStep.Interval, flow.uiState.value.step)
        flow.onPageChanged(0)
        assertEquals(OnboardingStep.Introduction, flow.uiState.value.step)
    }

    @Test
    fun `첫 페이지의 뒤로 가기와 범위를 벗어난 페이지 변경은 무시한다`() {
        val flow = OnboardingViewModel()
        flow.back()
        flow.onPageChanged(-1)
        flow.onPageChanged(4)
        assertEquals(OnboardingStep.Introduction, flow.uiState.value.step)
        flow.onPageChanged(2)
        flow.onPageChanged(Int.MAX_VALUE)
        assertEquals(OnboardingStep.Interval, flow.uiState.value.step)
    }

    @Test
    fun `완료를 연속으로 눌러도 같은 토큰의 요청은 한 번만 수락한다`() {
        val flow = OnboardingViewModel()
        flow.onPageChanged(3)
        assertEquals(OnboardingAdvance.Complete, flow.requestNext())
        repeat(3) {
            assertEquals(OnboardingAdvance.None, flow.requestNext())
        }
        assertTrue(flow.uiState.value.isCompletionRequested)
    }

    @Test
    fun `완료 상태 저장에 실패하면 새 토큰으로 재시도하고 이전 요청은 무시한다`() {
        val flow = OnboardingViewModel()
        flow.onPageChanged(3)
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(0))
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(1))
        assertEquals(OnboardingAdvance.None, flow.requestNext(1))
        assertEquals(OnboardingAdvance.None, flow.requestNext(0))
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(2))
        assertEquals(OnboardingAdvance.None, flow.requestNext(1))
    }

    @Test
    fun `완료 요청 뒤의 스와이프와 뒤로 가기는 마지막 페이지를 바꾸지 않는다`() {
        val flow = OnboardingViewModel()
        flow.onPageChanged(3)
        flow.requestNext()
        flow.back()
        flow.onPageChanged(0)
        flow.onPageChanged(2)
        assertEquals(OnboardingStep.Departure, flow.uiState.value.step)
        assertTrue(flow.uiState.value.isCompletionRequested)
    }

    @Test
    fun `음수 재시도 토큰은 완료 요청으로 수락하지 않는다`() {
        val flow = OnboardingViewModel()
        flow.onPageChanged(3)
        assertEquals(OnboardingAdvance.None, flow.requestNext(-1))
        assertFalse(flow.uiState.value.isCompletionRequested)
        assertEquals(OnboardingAdvance.Complete, flow.requestNext())
    }
}
