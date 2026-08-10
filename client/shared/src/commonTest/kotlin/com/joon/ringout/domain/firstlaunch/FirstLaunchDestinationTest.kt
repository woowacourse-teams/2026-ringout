package com.joon.ringout.domain.firstlaunch

import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermDefinition
import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms
import kotlin.test.Test
import kotlin.test.assertEquals

class FirstLaunchDestinationTest {
    @Test
    fun routesToOnboardingWhenNoValuesAreStored() {
        assertEquals(
            AppEntryDestination.Onboarding,
            determineAppEntryDestination(FirstLaunchStatus(), currentTerms),
        )
    }

    @Test
    fun onboardingTakesPriorityOverExistingConsentRecords() {
        assertEquals(
            AppEntryDestination.Onboarding,
            determineAppEntryDestination(
                FirstLaunchStatus(termConsents = validConsentRecords()),
                currentTerms,
            ),
        )
    }

    @Test
    fun routesToTermsWhenOnlyOnboardingIsCompleted() {
        assertEquals(
            AppEntryDestination.TermsAgreement,
            determineAppEntryDestination(
                FirstLaunchStatus(isOnboardingCompleted = true),
                currentTerms,
            ),
        )
    }

    @Test
    fun routesToHomeWhenEveryRequiredConsentRecordIsComplete() {
        assertEquals(
            AppEntryDestination.Home,
            determineAppEntryDestination(completedStatus(), currentTerms),
        )
    }

    @Test
    fun routesToTermsWhenARequiredRecordIsMissing() {
        val records = validConsentRecords().toMutableMap().apply {
            remove(currentTerms.first().id)
        }

        assertEquals(
            AppEntryDestination.TermsAgreement,
            determineAppEntryDestination(completedStatus(records), currentTerms),
        )
    }

    @Test
    fun routesToTermsWhenARequiredRecordHasNoAgreementTime() {
        assertRecordChangeRoutesToTerms { record -> record.copy(agreedAtEpochMillis = null) }
    }

    @Test
    fun routesToTermsWhenARequiredRecordIsNotAgreed() {
        assertRecordChangeRoutesToTerms { record -> record.copy(isAgreed = false) }
    }

    @Test
    fun routesToTermsWhenARequiredRecordWasStoredAsOptional() {
        assertRecordChangeRoutesToTerms { record -> record.copy(termType = TermType.OPTIONAL) }
    }

    @Test
    fun routesToTermsWhenARequiredRecordHasBlankStoredMetadata() {
        assertRecordChangeRoutesToTerms { record -> record.copy(termName = "") }
        assertRecordChangeRoutesToTerms { record -> record.copy(termVersion = "") }
    }

    @Test
    fun storedVersionIsNotComparedWithTheCurrentDefinition() {
        assertRecordChangeRoutesToHome { record -> record.copy(termVersion = "old-version") }
    }

    private fun assertRecordChangeRoutesToTerms(
        transform: (TermConsentRecord) -> TermConsentRecord,
    ) {
        assertEquals(
            AppEntryDestination.TermsAgreement,
            determineAppEntryDestination(statusWithFirstRecordChanged(transform), currentTerms),
        )
    }

    private fun assertRecordChangeRoutesToHome(
        transform: (TermConsentRecord) -> TermConsentRecord,
    ) {
        assertEquals(
            AppEntryDestination.Home,
            determineAppEntryDestination(statusWithFirstRecordChanged(transform), currentTerms),
        )
    }

    private fun statusWithFirstRecordChanged(
        transform: (TermConsentRecord) -> TermConsentRecord,
    ): FirstLaunchStatus {
        val records = validConsentRecords().toMutableMap()
        val firstId = currentTerms.first().id
        records[firstId] = transform(requireNotNull(records[firstId]))
        return completedStatus(records)
    }
}

private fun completedStatus(
    records: Map<com.joon.ringout.domain.terms.TermId, TermConsentRecord> = validConsentRecords(),
) = FirstLaunchStatus(
    isOnboardingCompleted = true,
    termConsents = records,
)

private fun validConsentRecords() = currentTerms.associate { definition ->
    definition.id to definition.toConsentRecord()
}

private fun TermDefinition.toConsentRecord() = TermConsentRecord(
    id = id,
    termName = name,
    termVersion = version,
    termType = type,
    isAgreed = true,
    agreedAtEpochMillis = 1_234L,
)
