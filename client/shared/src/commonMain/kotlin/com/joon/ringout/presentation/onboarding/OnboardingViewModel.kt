package com.joon.ringout.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.joon.ringout.analytics.AnalyticsOnboardingStep
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class OnboardingStep { Introduction, Destination, Interval, Departure }

internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Introduction,
    val isCompletionRequested: Boolean = false,
) {
    val steps: List<OnboardingStep> get() = OnboardingStep.entries
    val currentStepIndex: Int get() = steps.indexOf(step)
    val isLastStep: Boolean get() = step == steps.last()
}

internal enum class OnboardingAdvance { None, Complete }

internal class OnboardingViewModel(
    private val analytics: OnboardingAnalyticsRecorder = NoOpOnboardingAnalyticsRecorder,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableUiState.asStateFlow()

    private var lastCompletionToken: Int? = null
    private var lastSubmittedToken: Int? = null
    private var lastViewedStep: OnboardingStep? = null

    /** Recomposition and app resume do not count as a new page entry. */
    fun onStepVisible() {
        val state = uiState.value
        if (lastViewedStep == state.step) return
        lastViewedStep = state.step
        runCatching {
            analytics.recordOnboardingStarted(state.steps.size)
            analytics.recordOnboardingStepViewed(state.step.analyticsStep(), state.steps.size)
        }
    }

    fun onSubmitAccepted() {
        val token = lastCompletionToken ?: return
        if (lastSubmittedToken == token) return
        lastSubmittedToken = token
        runCatching { analytics.recordOnboardingSubmitted(uiState.value.steps.size) }
    }

    fun requestNext(completionRetryToken: Int = 0): OnboardingAdvance {
        val state = uiState.value
        if (!state.isLastStep) {
            onPageChanged(state.currentStepIndex + 1)
            return OnboardingAdvance.None
        }

        // Retry tokens increase only after a failed preferences write. Ignore stale callbacks
        // and duplicate taps while that write is in flight or its result is being observed.
        if (completionRetryToken < 0 ||
            lastCompletionToken?.let { completionRetryToken <= it } == true
        ) {
            return OnboardingAdvance.None
        }
        lastCompletionToken = completionRetryToken
        mutableUiState.value = state.copy(isCompletionRequested = true)
        return OnboardingAdvance.Complete
    }

    fun onPageChanged(index: Int) {
        val state = uiState.value
        if (state.isCompletionRequested) return
        val step = state.steps.getOrNull(index) ?: return
        mutableUiState.value = state.copy(step = step)
    }

    fun back() {
        onPageChanged(uiState.value.currentStepIndex - 1)
    }
}

private fun OnboardingStep.analyticsStep(): AnalyticsOnboardingStep = when (this) {
    OnboardingStep.Introduction -> AnalyticsOnboardingStep.Introduction
    OnboardingStep.Destination -> AnalyticsOnboardingStep.Destination
    OnboardingStep.Interval -> AnalyticsOnboardingStep.Interval
    OnboardingStep.Departure -> AnalyticsOnboardingStep.Departure
}
