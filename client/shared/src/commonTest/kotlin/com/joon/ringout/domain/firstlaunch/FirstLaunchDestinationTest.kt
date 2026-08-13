package com.joon.ringout.domain.firstlaunch

import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.domain.terms.TermType
import kotlin.test.Test
import kotlin.test.assertEquals

class FirstLaunchDestinationTest {
    @Test
    fun routesToOnboardingWhenNoValuesAreStored() {
        assertEquals(
            AppEntryDestination.Onboarding,
            determineAppEntryDestination(FirstLaunchStatus()),
        )
    }

    @Test
    fun routesToHomeWhenOnboardingIsCompletedWithoutConsentRecords() {
        assertEquals(
            AppEntryDestination.Home,
            determineAppEntryDestination(FirstLaunchStatus(isOnboardingCompleted = true)),
        )
    }

    @Test
    fun onboardingTakesPriorityOverExistingConsentRecords() {
        assertEquals(
            AppEntryDestination.Onboarding,
            determineAppEntryDestination(
                FirstLaunchStatus(termConsents = mapOf(TermId.Service to malformedConsentRecord)),
            ),
        )
    }

    @Test
    fun routesToHomeRegardlessOfMissingMalformedOrStaleConsentRecords() {
        assertEquals(
            AppEntryDestination.Home,
            determineAppEntryDestination(
                FirstLaunchStatus(
                    isOnboardingCompleted = true,
                    termConsents = mapOf(TermId.Service to malformedConsentRecord),
                ),
            ),
        )
    }
}

private val malformedConsentRecord = TermConsentRecord(
    id = TermId.Service,
    termName = "",
    termVersion = "old-version",
    termType = TermType.OPTIONAL,
    isAgreed = false,
    agreedAtEpochMillis = null,
)
