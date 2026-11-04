package com.joon.ringout.domain.preferences

import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus

data class AppBootstrapSnapshot(
    val themeMode: ThemeMode? = null,
    val firstLaunchStatus: FirstLaunchStatus,
    val isAlarmCreationGuidePending: Boolean = false,
    val legacyEntryState: LegacyEntryState = LegacyEntryState(),
    val installationEntryState: InstallationEntryState? = null,
)

/** Raw legacy key presence is retained until the one-time entry-policy migration completes. */
data class LegacyEntryState(
    val hasOnboardingKey: Boolean = false,
    val isOnboardingCompleted: Boolean? = null,
    val hasAlarmGuidePendingKey: Boolean = false,
    val isAlarmGuidePending: Boolean? = null,
) {
    val hasAnyEntryKey: Boolean get() = hasOnboardingKey || hasAlarmGuidePendingKey
}

/** Installation-scoped entry and logout recovery values stored separately from account data. */
data class InstallationEntryState(
    val policyVersion: Int? = null,
    val isOnboardingCompleted: Boolean = false,
    val isAlarmCreationGuideCompleted: Boolean = false,
    val hasLegacyRuntimeExceptionPending: Boolean = false,
    val isLogoutCleanupPending: Boolean = false,
)
