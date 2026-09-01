package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun OnboardingRoute(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
    completionRetryToken: Int = 0,
    pages: List<OnboardingPageContent> = defaultOnboardingPages,
) {
    val viewModel = viewModel { OnboardingViewModel() }
    OnboardingScreen(
        pages = pages,
        currentPageIndex = viewModel.uiState.currentPageIndex,
        onNext = {
            when (viewModel.requestNext(pages.size, completionRetryToken)) {
                OnboardingAdvance.Complete -> onComplete()
                OnboardingAdvance.IgnoredAlreadyComplete,
                is OnboardingAdvance.MoveTo,
                -> Unit
            }
        },
        modifier = modifier,
        completionEnabled = completionEnabled,
    )
}
