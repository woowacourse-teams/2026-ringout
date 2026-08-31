package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun OnboardingRoute(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
    completionRetryToken: Int = 0,
    pages: List<OnboardingPageContent> = defaultOnboardingPages,
) {
    // 독립 소유자는 구성 변경 시에도 유지되며, 온보딩이 끝나면 정리된다.
    val owner = rememberViewModelStoreOwner(savedStateRegistryOwner = null)
    val viewModel = viewModel(viewModelStoreOwner = owner) { OnboardingViewModel() }
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
