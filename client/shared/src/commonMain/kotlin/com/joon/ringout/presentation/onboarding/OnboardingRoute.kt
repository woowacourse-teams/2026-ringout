package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
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
    permissionController: OnboardingPermissionController? = null,
) {
    val flow = viewModel { OnboardingViewModel(analytics = analytics) }
    val state = flow.uiState.collectAsStateWithLifecycle().value
    val controller = permissionController ?: rememberOnboardingPermissionController()
    val currentOnComplete by rememberUpdatedState(onComplete)
    val currentRetryToken by rememberUpdatedState(completionRetryToken)

    LifecycleResumeEffect(controller, flow) {
        val requestId = flow.uiState.value.permissionRequestId
        controller.refresh { permissions ->
            flow.onPermissionsRefreshed(permissions, requestId)
        }
        onPauseOrDispose { }
    }

    LaunchedEffect(flow, state.completionRequestToken) {
        if (state.completionRequestToken != null && flow.onSubmitAccepted()) {
            currentOnComplete(flow.uiState.value.steps.size)
        }
    }

    fun requestPermission(permission: OnboardingPermission) {
        val requestId = flow.uiState.value.permissionRequestId
        var handled = false
        controller.request(permission) { status ->
            if (!handled) {
                handled = true
                // Recheck both permissions: the user can change either one while in Settings.
                controller.refresh { refreshed ->
                    val permissions = if (status == OnboardingPermissionStatus.Unavailable) {
                        refreshed.withStatus(permission, status)
                    } else {
                        refreshed
                    }
                    flow.onPermissionResult(permission, requestId, permissions, currentRetryToken)
                }
            }
        }
    }

    PlatformBackHandler(enabled = state.currentStepIndex > 0) {
        if (completionEnabled && state.requestingPermission == null) flow.back()
    }
    OnboardingScreen(
        uiState = state,
        onPageChanged = { if (completionEnabled) flow.onPageChanged(it) },
        onPageSettled = flow::onStepVisible,
        onNext = {
            // Rapid introduction taps may advance, but an old page must not request a permission.
            val latest = flow.uiState.value
            if (completionEnabled && (latest.step.permission == null || latest.step == state.step)) {
                when (flow.requestNext(completionRetryToken)) {
                    OnboardingAdvance.RequestLocationPermission -> requestPermission(OnboardingPermission.Location)
                    OnboardingAdvance.RequestNotificationPermission -> requestPermission(OnboardingPermission.Notifications)
                    else -> Unit
                }
            }
        },
        modifier = modifier,
        completionEnabled = completionEnabled,
        completionFailed = completionEnabled && completionRetryToken > 0,
    )
}
