package com.joon.ringout.domain.firstlaunch

import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermId

data class FirstLaunchStatus(
    val isOnboardingCompleted: Boolean = false,
    val termConsents: Map<TermId, TermConsentRecord> = emptyMap(),
)

enum class AppEntryDestination {
    Onboarding,
    Home,
}

fun determineAppEntryDestination(status: FirstLaunchStatus): AppEntryDestination =
    if (status.isOnboardingCompleted) {
        AppEntryDestination.Home
    } else {
        AppEntryDestination.Onboarding
    }
