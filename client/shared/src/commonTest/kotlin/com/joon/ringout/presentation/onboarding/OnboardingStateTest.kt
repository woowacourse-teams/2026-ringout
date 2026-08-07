package com.joon.ringout.presentation.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OnboardingStateTest {
    @Test
    fun startsAtFirstPage() {
        val viewModel = OnboardingViewModel()

        assertEquals(0, viewModel.uiState.currentPageIndex)
    }

    @Test
    fun movesForwardOnePageAtATime() {
        val viewModel = OnboardingViewModel()

        assertEquals(OnboardingAdvance.MoveTo(1), viewModel.requestNext(pageCount = 4))
        assertEquals(OnboardingAdvance.MoveTo(2), viewModel.requestNext(pageCount = 4))
        assertEquals(OnboardingAdvance.MoveTo(3), viewModel.requestNext(pageCount = 4))
        assertEquals(3, viewModel.uiState.currentPageIndex)
    }

    @Test
    fun completesWithoutMovingPastLastPage() {
        val viewModel = OnboardingViewModel()
        repeat(3) { viewModel.requestNext(pageCount = 4) }

        assertEquals(OnboardingAdvance.Complete, viewModel.requestNext(pageCount = 4))
        assertEquals(3, viewModel.uiState.currentPageIndex)
    }

    @Test
    fun ignoresCompletionAfterFirstRequest() {
        val viewModel = OnboardingViewModel()
        repeat(3) { viewModel.requestNext(pageCount = 4) }

        assertEquals(OnboardingAdvance.Complete, viewModel.requestNext(pageCount = 4))
        assertEquals(
            OnboardingAdvance.IgnoredAlreadyComplete,
            viewModel.requestNext(pageCount = 4),
        )
    }

    @Test
    fun completesSinglePageOnFirstRequest() {
        val viewModel = OnboardingViewModel()

        assertEquals(OnboardingAdvance.Complete, viewModel.requestNext(pageCount = 1))
        assertEquals(0, viewModel.uiState.currentPageIndex)
    }

    @Test
    fun rejectsEmptyPageCount() {
        val viewModel = OnboardingViewModel()

        assertFailsWith<IllegalArgumentException> {
            viewModel.requestNext(pageCount = 0)
        }
    }
}
