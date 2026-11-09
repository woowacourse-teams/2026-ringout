package com.joon.ringout.presentation.arrival

import com.joon.ringout.domain.firstlaunch.AppEntryDestination

/** Legacy missions can finish before onboarding; acknowledgement returns to the same entry gate. */
internal fun shouldEnableRestrictedArrival(
    isAppReady: Boolean,
    entryDestination: AppEntryDestination?,
    areGuideTermsAllowed: Boolean,
): Boolean = isAppReady && when (entryDestination) {
    AppEntryDestination.Onboarding,
    AppEntryDestination.Login,
    -> true
    AppEntryDestination.Guide -> !areGuideTermsAllowed
    AppEntryDestination.LogoutCleaning,
    AppEntryDestination.Home,
    null,
    -> false
}
