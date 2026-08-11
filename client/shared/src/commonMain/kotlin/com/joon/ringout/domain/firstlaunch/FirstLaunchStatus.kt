package com.joon.ringout.domain.firstlaunch

import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermDefinition
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.domain.terms.TermType

data class FirstLaunchStatus(
    val isOnboardingCompleted: Boolean = false,
    val termConsents: Map<TermId, TermConsentRecord> = emptyMap(),
)

enum class AppEntryDestination {
    Onboarding,
    TermsAgreement,
    Home,
}

fun determineAppEntryDestination(
    status: FirstLaunchStatus,
    terms: List<TermDefinition>,
): AppEntryDestination {
    if (!status.isOnboardingCompleted) return AppEntryDestination.Onboarding

    val requiredTermsAreAgreed = terms
        .filter { definition -> definition.type == TermType.REQUIRED }
        .all { definition ->
            val record = status.termConsents[definition.id]
            record != null &&
                record.termName.isNotBlank() &&
                record.termVersion.isNotBlank() &&
                record.termType == TermType.REQUIRED &&
                record.isAgreed &&
                record.agreedAtEpochMillis != null
        }

    return if (requiredTermsAreAgreed) {
        AppEntryDestination.Home
    } else {
        AppEntryDestination.TermsAgreement
    }
}
