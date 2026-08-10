package com.joon.ringout.domain.preferences

import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.terms.TermConsentRecord
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val bootstrapState: Flow<AppBootstrapSnapshot>

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun markOnboardingCompleted()

    suspend fun saveTermConsents(records: List<TermConsentRecord>)
}
