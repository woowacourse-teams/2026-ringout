package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

data class OnboardingUiState(
    val currentPageIndex: Int = 0,
)

sealed interface OnboardingAdvance {
    data class MoveTo(val index: Int) : OnboardingAdvance

    data object Complete : OnboardingAdvance

    data object IgnoredAlreadyComplete : OnboardingAdvance
}

class OnboardingViewModel : ViewModel() {
    var uiState by mutableStateOf(OnboardingUiState())
        private set

    private var hasCompleted = false

    fun requestNext(pageCount: Int): OnboardingAdvance {
        require(pageCount > 0) { "Onboarding requires at least one page." }
        require(uiState.currentPageIndex in 0 until pageCount) {
            "Current page must be within the supplied page count."
        }

        if (hasCompleted) {
            return OnboardingAdvance.IgnoredAlreadyComplete
        }

        if (uiState.currentPageIndex == pageCount - 1) {
            hasCompleted = true
            return OnboardingAdvance.Complete
        }

        val nextPageIndex = uiState.currentPageIndex + 1
        uiState = uiState.copy(currentPageIndex = nextPageIndex)
        return OnboardingAdvance.MoveTo(nextPageIndex)
    }
}
