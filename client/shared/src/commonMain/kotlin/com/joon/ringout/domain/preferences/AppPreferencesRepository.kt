package com.joon.ringout.domain.preferences

import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.terms.TermConsentRecord
import kotlinx.coroutines.flow.Flow

interface AppPreferencesRepository {
    val bootstrapState: Flow<AppBootstrapSnapshot>

    suspend fun initializeThemeModeIfMissing(themeMode: ThemeMode)

    suspend fun setThemeMode(themeMode: ThemeMode)

    suspend fun markOnboardingCompleted()

    suspend fun completeAlarmCreationGuide()

    suspend fun initializeEntryPolicy(
        authSessionState: AuthSessionState,
        hasLegacyAlarmOrRuntimeEvidence: Boolean,
    )

    suspend fun setLogoutCleanupPending(pending: Boolean)

    suspend fun setLegacyRuntimeExceptionPending(pending: Boolean)

    suspend fun saveTermConsents(records: List<TermConsentRecord>)
}
