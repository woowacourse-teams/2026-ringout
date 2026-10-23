package com.joon.ringout.presentation.onboarding

import androidx.lifecycle.ViewModel
import com.joon.ringout.analytics.AnalyticsOnboardingStep
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class OnboardingStep {
    Introduction, Destination, Interval, Departure, LocationPermission, NotificationPermission,
}

internal val OnboardingStep.permission: OnboardingPermission?
    get() = when (this) {
        OnboardingStep.LocationPermission -> OnboardingPermission.Location
        OnboardingStep.NotificationPermission -> OnboardingPermission.Notifications
        else -> null
    }

internal data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Introduction,
    val isCompletionRequested: Boolean = false,
    val permissions: OnboardingPermissionState = OnboardingPermissionState(),
    val requestingPermission: OnboardingPermission? = null,
    val permissionRequestId: Int = 0,
    val completionRequestToken: Int? = null,
) {
    val steps: List<OnboardingStep> get() = OnboardingStep.entries
    val currentStepIndex: Int get() = steps.indexOf(step)
    val isLastStep: Boolean get() = step == steps.last()
    val permissionStatus: OnboardingPermissionStatus? get() = step.permission?.let { permissions[it] }
}

internal enum class OnboardingAdvance { None, RequestLocationPermission, RequestNotificationPermission, Complete }

internal class OnboardingViewModel(
    private val analytics: OnboardingAnalyticsRecorder = NoOpOnboardingAnalyticsRecorder,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = mutableUiState.asStateFlow()

    private var lastCompletionToken: Int? = null
    private var lastSubmittedToken: Int? = null
    private var lastViewedStep: OnboardingStep? = null

    /** Recomposition and app resume do not count as a new page entry. */
    fun onStepVisible(index: Int) {
        val state = uiState.value
        val step = state.steps.getOrNull(index) ?: return
        if (lastViewedStep == step) return
        lastViewedStep = step
        runCatching {
            analytics.recordOnboardingStarted(state.steps.size)
            analytics.recordOnboardingStepViewed(step.analyticsStep(), state.steps.size)
        }
    }

    fun onSubmitAccepted(): Boolean {
        val token = lastCompletionToken ?: return false
        if (lastSubmittedToken == token) return false
        lastSubmittedToken = token
        runCatching { analytics.recordOnboardingSubmitted(uiState.value.steps.size) }
        return true
    }

    fun requestNext(completionRetryToken: Int = 0): OnboardingAdvance {
        val state = uiState.value
        if (state.requestingPermission != null) return OnboardingAdvance.None
        if (state.isCompletionRequested) return requestCompletion(completionRetryToken)
        val permission = state.step.permission
        if (permission == null) {
            onPageChanged(state.currentStepIndex + 1)
            return OnboardingAdvance.None
        }
        if (state.permissions[permission] == OnboardingPermissionStatus.Unknown) return OnboardingAdvance.None
        mutableUiState.value = state.copy(
            requestingPermission = permission,
            permissionRequestId = state.permissionRequestId + 1,
        )
        return when (permission) {
            OnboardingPermission.Location -> OnboardingAdvance.RequestLocationPermission
            OnboardingPermission.Notifications -> OnboardingAdvance.RequestNotificationPermission
        }
    }

    fun onPermissionsRefreshed(
        permissions: OnboardingPermissionState,
        requestId: Int = uiState.value.permissionRequestId,
    ) {
        val state = uiState.value
        if (state.requestingPermission != null || state.isCompletionRequested || state.permissionRequestId != requestId) return
        mutableUiState.value = state.copy(permissions = permissions)
    }

    fun onPermissionResult(
        permission: OnboardingPermission,
        requestId: Int,
        permissions: OnboardingPermissionState,
        completionRetryToken: Int = 0,
    ): OnboardingAdvance {
        val state = uiState.value
        if (state.requestingPermission != permission || state.permissionRequestId != requestId) {
            return OnboardingAdvance.None
        }
        mutableUiState.value = state.copy(permissions = permissions, requestingPermission = null)
        if (permissions[permission] != OnboardingPermissionStatus.Granted) return OnboardingAdvance.None
        if (permission == OnboardingPermission.Location) {
            onPageChanged(OnboardingStep.NotificationPermission.ordinal)
            return OnboardingAdvance.None
        }
        if (permissions.location != OnboardingPermissionStatus.Granted) {
            onPageChanged(OnboardingStep.LocationPermission.ordinal)
            return OnboardingAdvance.None
        }
        return requestCompletion(completionRetryToken)
    }

    private fun requestCompletion(completionRetryToken: Int): OnboardingAdvance {
        val state = uiState.value
        if (!state.isLastStep || state.permissions.location != OnboardingPermissionStatus.Granted ||
            state.permissions.notifications != OnboardingPermissionStatus.Granted
        ) return OnboardingAdvance.None

        // Retry tokens increase only after a failed preferences write. Ignore stale callbacks
        // and duplicate taps while that write is in flight or its result is being observed.
        if (completionRetryToken < 0 ||
            lastCompletionToken?.let { completionRetryToken <= it } == true
        ) {
            return OnboardingAdvance.None
        }
        lastCompletionToken = completionRetryToken
        mutableUiState.value = state.copy(isCompletionRequested = true, completionRequestToken = completionRetryToken)
        return OnboardingAdvance.Complete
    }

    fun onPageChanged(index: Int) {
        val state = uiState.value
        if (state.isCompletionRequested || state.requestingPermission != null) return
        val step = state.steps.getOrNull(index) ?: return
        if (step == OnboardingStep.NotificationPermission &&
            state.permissions.location != OnboardingPermissionStatus.Granted
        ) return
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
    OnboardingStep.LocationPermission -> AnalyticsOnboardingStep.LocationPermission
    OnboardingStep.NotificationPermission -> AnalyticsOnboardingStep.NotificationPermission
}
