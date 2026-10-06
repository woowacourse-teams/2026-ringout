package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.joon.ringout.analytics.AnalyticsOnboardingStep
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import com.joon.ringout.presentation.alarmsetup.AlarmSetupUiState

internal enum class OnboardingStep(val title: String, val description: String) {
    Time("알람이 울릴 시간을 정해주세요", "설정한 시간에 알람이 울려요."),
    Weekdays("알람이 울릴 요일을 선택해주세요", "설정한 요일마다 알람이 울려요."),
    Destination("목적지를 선택해주세요", "설정한 목적지에 도착해야\n알람이 반복해서 울리지 않아요."),
    Interval("알람의 간격을 선택해주세요", "목적지에 도착하기 전까지\n설정한 간격으로 알람이 반복해서 울려요."),
    Sound("알람음을 선택해주세요", "선택한 알람음으로 알람이 울려요."),
}

internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Time,
    val includesSoundSelection: Boolean = true,
    val destinationRequestId: Long = 0,
    val isDestinationOpen: Boolean = false,
    val isAlarmSaved: Boolean = false,
) {
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { includesSoundSelection || it != OnboardingStep.Sound }

    val currentStepIndex: Int get() = steps.indexOf(step)
    val isLastStep: Boolean get() = step == steps.last()
}

internal enum class OnboardingAdvance { None, SaveAlarm, Complete }

internal class OnboardingViewModel(
    includesSoundSelection: Boolean = true,
    private val analytics: OnboardingAnalyticsRecorder = NoOpOnboardingAnalyticsRecorder,
) : ViewModel() {
    var uiState by mutableStateOf(OnboardingUiState(includesSoundSelection = includesSoundSelection))
        private set

    private var lastCompletionToken: Int? = null
    private var lastViewedStep: OnboardingStep? = null

    /** Called only when the step is displayed; recomposition and app resume are not entries. */
    fun onStepVisible() {
        if (uiState.isDestinationOpen || lastViewedStep == uiState.step) return
        lastViewedStep = uiState.step
        runCatching {
            analytics.recordOnboardingStarted(uiState.steps.size)
            analytics.recordOnboardingStepViewed(uiState.step.analyticsStep(), uiState.steps.size)
        }
    }

    /** Called after a save request (or completion-only retry) has been accepted. */
    fun onSubmitAccepted() {
        if (!uiState.isLastStep || uiState.isDestinationOpen) return
        runCatching { analytics.recordOnboardingSubmitted(uiState.steps.size) }
    }

    fun requestNext(alarm: AlarmSetupUiState, completionRetryToken: Int): OnboardingAdvance {
        if (alarm.isSaveInProgress || uiState.isDestinationOpen) return OnboardingAdvance.None
        if (uiState.isAlarmSaved) return requestCompletion(completionRetryToken)
        if (uiState.step == OnboardingStep.Destination && !alarm.canSave) return OnboardingAdvance.None
        if (uiState.isLastStep) {
            return if (alarm.canSave) OnboardingAdvance.SaveAlarm else OnboardingAdvance.None
        }
        uiState = uiState.copy(step = uiState.steps[uiState.currentStepIndex + 1])
        return OnboardingAdvance.None
    }

    fun onAlarmSaved(completionRetryToken: Int): OnboardingAdvance {
        uiState = uiState.copy(isAlarmSaved = true)
        return requestCompletion(completionRetryToken)
    }

    private fun requestCompletion(token: Int): OnboardingAdvance {
        if (lastCompletionToken == token) return OnboardingAdvance.None
        lastCompletionToken = token
        return OnboardingAdvance.Complete
    }

    fun back() {
        if (uiState.isAlarmSaved) return
        uiState = when {
            uiState.isDestinationOpen -> uiState.copy(isDestinationOpen = false)
            uiState.currentStepIndex > 0 -> uiState.copy(step = uiState.steps[uiState.currentStepIndex - 1])
            else -> uiState
        }
    }

    fun openDestination() {
        if (uiState.step != OnboardingStep.Destination || uiState.isAlarmSaved) return
        lastViewedStep = null
        uiState = uiState.copy(
            destinationRequestId = uiState.destinationRequestId + 1,
            isDestinationOpen = true,
        )
    }

    fun acceptDestination(requestId: Long): Boolean {
        if (!uiState.isDestinationOpen || uiState.destinationRequestId != requestId) return false
        uiState = uiState.copy(isDestinationOpen = false)
        return true
    }
}

private fun OnboardingStep.analyticsStep(): AnalyticsOnboardingStep = when (this) {
    OnboardingStep.Time -> AnalyticsOnboardingStep.Time
    OnboardingStep.Weekdays -> AnalyticsOnboardingStep.Weekdays
    OnboardingStep.Destination -> AnalyticsOnboardingStep.Destination
    OnboardingStep.Interval -> AnalyticsOnboardingStep.Interval
    OnboardingStep.Sound -> AnalyticsOnboardingStep.Sound
}
