package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import com.joon.ringout.presentation.destination.PlatformBackHandler

@Composable
internal fun OnboardingRoute(
    analytics: OnboardingAnalyticsRecorder,
    onComplete: (stepCount: Int) -> Unit,
    modifier: Modifier = Modifier,
    completionEnabled: Boolean = true,
    completionRetryToken: Int = 0,
) {
    val flow = viewModel { OnboardingViewModel(analytics = analytics) }
    val state = flow.uiState.collectAsStateWithLifecycle().value

    PlatformBackHandler(enabled = state.currentStepIndex > 0) {
        if (completionEnabled) flow.back()
    }
    OnboardingScreen(
        uiState = state,
        onPageChanged = { if (completionEnabled) flow.onPageChanged(it) },
        onPageSettled = flow::onStepVisible,
        onNext = {
            // Accept rapid next taps against the latest target, but never let an old page submit.
            if (completionEnabled &&
                (!flow.uiState.value.isLastStep || state.isLastStep) &&
                flow.requestNext(completionRetryToken) == OnboardingAdvance.Complete
            ) {
                flow.onSubmitAccepted()
                onComplete(state.steps.size)
            }
        },
        modifier = modifier,
        completionEnabled = completionEnabled,
        completionFailed = completionEnabled && completionRetryToken > 0,
    )
}
