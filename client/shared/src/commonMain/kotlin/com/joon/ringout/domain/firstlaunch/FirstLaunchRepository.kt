package com.joon.ringout.domain.firstlaunch

import com.joon.ringout.domain.terms.TermConsentRecord
import kotlinx.coroutines.flow.Flow

interface FirstLaunchRepository {
    val status: Flow<FirstLaunchStatus>

    suspend fun markOnboardingCompleted()

    suspend fun saveTermConsents(records: List<TermConsentRecord>)
}
