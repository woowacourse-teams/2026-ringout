package com.joon.ringout.domain.firstlaunch

import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.preferences.InstallationEntryState
import com.joon.ringout.domain.preferences.LegacyEntryState

data class FirstLaunchStatus(
    val isOnboardingCompleted: Boolean = false,
    val termConsents: Map<TermId, TermConsentRecord> = emptyMap(),
)

enum class AppEntryDestination {
    LogoutCleaning,
    Onboarding,
    Login,
    Guide,
    Home,
}

fun determineAppEntryDestination(
    installation: InstallationEntryState,
    authSessionState: AuthSessionState,
    isAuthFlowInProgress: Boolean = false,
): AppEntryDestination? {
    if (installation.isLogoutCleanupPending) return AppEntryDestination.LogoutCleaning
    if (authSessionState == AuthSessionState.Restoring) return null
    if (!installation.isOnboardingCompleted) return AppEntryDestination.Onboarding
    if (isAuthFlowInProgress || authSessionState != AuthSessionState.Authenticated) {
        return AppEntryDestination.Login
    }
    return if (installation.isAlarmCreationGuideCompleted) {
        AppEntryDestination.Home
    } else {
        AppEntryDestination.Guide
    }
}

fun shouldShowLimitedRuntimeControls(
    installation: InstallationEntryState,
    authSessionState: AuthSessionState,
    hasActiveRuntime: Boolean,
): Boolean = installation.policyVersion == EntryPolicyVersion &&
    !installation.isLogoutCleanupPending &&
    authSessionState != AuthSessionState.Restoring &&
    authSessionState != AuthSessionState.Authenticated &&
    hasActiveRuntime &&
    (installation.isOnboardingCompleted || installation.hasLegacyRuntimeExceptionPending)

fun migratedInstallationEntryState(
    authSessionState: AuthSessionState,
    legacy: LegacyEntryState,
    hasLegacyAlarmOrRuntimeEvidence: Boolean,
): InstallationEntryState {
    require(authSessionState != AuthSessionState.Restoring) {
        "Authentication must be restored before entry policy migration."
    }

    val isExistingAuthenticatedInstall = authSessionState == AuthSessionState.Authenticated ||
        authSessionState == AuthSessionState.ReauthenticationRequired
    if (!isExistingAuthenticatedInstall) {
        val isExistingUnauthenticatedInstall = legacy.hasAnyEntryKey || hasLegacyAlarmOrRuntimeEvidence
        return InstallationEntryState(
            policyVersion = EntryPolicyVersion,
            isOnboardingCompleted = false,
            isAlarmCreationGuideCompleted = false,
            hasLegacyRuntimeExceptionPending = isExistingUnauthenticatedInstall,
        )
    }

    val guideCompleted = when {
        legacy.isAlarmGuidePending != null -> !legacy.isAlarmGuidePending
        legacy.isOnboardingCompleted == true -> true
        else -> false
    }
    return InstallationEntryState(
        policyVersion = EntryPolicyVersion,
        isOnboardingCompleted = true,
        isAlarmCreationGuideCompleted = guideCompleted,
    )
}

const val EntryPolicyVersion = 1
