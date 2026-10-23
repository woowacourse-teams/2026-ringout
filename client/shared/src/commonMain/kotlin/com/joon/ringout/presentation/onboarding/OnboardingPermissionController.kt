package com.joon.ringout.presentation.onboarding

import androidx.compose.runtime.Composable

enum class OnboardingPermission { Location, Notifications }

enum class OnboardingPermissionStatus {
    Unknown,
    Requestable,
    Granted,
    SettingsRequired,
    Restricted,
    ServicesDisabled,
    Unavailable,
}

data class OnboardingPermissionState(
    val location: OnboardingPermissionStatus = OnboardingPermissionStatus.Unknown,
    val notifications: OnboardingPermissionStatus = OnboardingPermissionStatus.Unknown,
) {
    operator fun get(permission: OnboardingPermission): OnboardingPermissionStatus = when (permission) {
        OnboardingPermission.Location -> location
        OnboardingPermission.Notifications -> notifications
    }

    fun withStatus(permission: OnboardingPermission, status: OnboardingPermissionStatus): OnboardingPermissionState =
        when (permission) {
            OnboardingPermission.Location -> copy(location = status)
            OnboardingPermission.Notifications -> copy(notifications = status)
        }
}

internal interface OnboardingPermissionController {
    fun refresh(onResult: (OnboardingPermissionState) -> Unit)

    /** Requests authorization, or opens Settings when another system prompt is unavailable.
     * Reports the actual permission state after the prompt or Settings has closed.
     */
    fun request(permission: OnboardingPermission, onResult: (OnboardingPermissionStatus) -> Unit)
}

@Composable
internal expect fun rememberOnboardingPermissionController(): OnboardingPermissionController
