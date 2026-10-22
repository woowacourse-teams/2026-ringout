package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
    val state by flow.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.step) {
        flow.onStepVisible()
    }
    PlatformBackHandler(enabled = state.currentStepIndex > 0) {
        if (completionEnabled) flow.back()
    }
    OnboardingScreen(
        uiState = state,
        onPageChanged = { if (completionEnabled) flow.onPageChanged(it) },
        onNext = {
            if (completionEnabled &&
                state.step == flow.uiState.value.step &&
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
