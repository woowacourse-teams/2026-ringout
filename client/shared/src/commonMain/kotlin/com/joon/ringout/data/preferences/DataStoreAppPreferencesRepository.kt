package com.joon.ringout.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus
import com.joon.ringout.domain.firstlaunch.EntryPolicyVersion
import com.joon.ringout.domain.firstlaunch.migratedInstallationEntryState
import com.joon.ringout.domain.preferences.AppBootstrapSnapshot
import com.joon.ringout.domain.preferences.InstallationEntryState
import com.joon.ringout.domain.preferences.LegacyEntryState
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermDefinition
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreAppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val installationDataStore: DataStore<Preferences> = dataStore,
    private val terms: List<TermDefinition> = currentTerms,
) : AppPreferencesRepository {
    override val bootstrapState: Flow<AppBootstrapSnapshot> = combine(
        dataStore.data,
        installationDataStore.data,
    ) { preferences, installationPreferences ->
        val installation = installationPreferences.toInstallationEntryState()
        AppBootstrapSnapshot(
            themeMode = ThemeMode.fromPersistedValue(preferences[ThemeModeKey]),
            firstLaunchStatus = preferences.toFirstLaunchStatus(),
            isAlarmCreationGuidePending = installation.policyVersion == EntryPolicyVersion &&
                !installation.isAlarmCreationGuideCompleted,
            legacyEntryState = preferences.toLegacyEntryState(),
            installationEntryState = installation,
        )
    }

    override suspend fun initializeThemeModeIfMissing(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            if (ThemeMode.fromPersistedValue(preferences[ThemeModeKey]) == null) {
                preferences[ThemeModeKey] = themeMode.persistedValue
            }
        }
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[ThemeModeKey] = themeMode.persistedValue
        }
    }

    override suspend fun markOnboardingCompleted() {
        installationDataStore.edit { preferences ->
            preferences[InstallationOnboardingCompletedKey] = true
        }
    }

    override suspend fun completeAlarmCreationGuide() {
        installationDataStore.edit { preferences ->
            preferences[InstallationAlarmGuideCompletedKey] = true
        }
    }

    override suspend fun initializeEntryPolicy(
        authSessionState: AuthSessionState,
        hasLegacyAlarmOrRuntimeEvidence: Boolean,
    ) {
        val legacy = dataStore.data.map { preferences -> preferences.toLegacyEntryState() }.first()
        installationDataStore.edit { preferences ->
            if (preferences[EntryPolicyVersionKey] == EntryPolicyVersion) return@edit
            val initial = migratedInstallationEntryState(
                authSessionState = authSessionState,
                legacy = legacy,
                hasLegacyAlarmOrRuntimeEvidence = hasLegacyAlarmOrRuntimeEvidence,
            )
            preferences[EntryPolicyVersionKey] = EntryPolicyVersion
            preferences[InstallationOnboardingCompletedKey] = initial.isOnboardingCompleted
            preferences[InstallationAlarmGuideCompletedKey] =
                initial.isAlarmCreationGuideCompleted
            preferences[LegacyRuntimeExceptionPendingKey] =
                initial.hasLegacyRuntimeExceptionPending
            if (LogoutCleanupPendingKey !in preferences) {
                preferences[LogoutCleanupPendingKey] = false
            }
        }
    }

    override suspend fun setLogoutCleanupPending(pending: Boolean) {
        installationDataStore.edit { preferences ->
            preferences[LogoutCleanupPendingKey] = pending
        }
    }

    override suspend fun setLegacyRuntimeExceptionPending(pending: Boolean) {
        installationDataStore.edit { preferences ->
            preferences[LegacyRuntimeExceptionPendingKey] = pending
        }
    }

    override suspend fun reserveLegacyAlarmMigrationAccount(accountId: String): String? {
        require(accountId.isNotBlank())
        installationDataStore.edit { preferences ->
            if (preferences[LegacyAlarmMigrationAccountIdKey] == null &&
                preferences[LegacyRuntimeExceptionPendingKey] == true
            ) {
                preferences[LegacyAlarmMigrationAccountIdKey] = accountId
            }
        }
        return installationDataStore.data.first()[LegacyAlarmMigrationAccountIdKey]
    }

    override suspend fun completeLegacyAlarmMigration(accountId: String) {
        require(accountId.isNotBlank())
        installationDataStore.edit { preferences ->
            check(preferences[LegacyAlarmMigrationAccountIdKey] == accountId) {
                "첫 로그인 알람 이관 계정이 일치하지 않습니다."
            }
            preferences[LegacyAlarmMigrationCompleteKey] = true
        }
    }

    override suspend fun saveTermConsents(records: List<TermConsentRecord>) {
        val recordsById = records.associateBy(TermConsentRecord::id)
        require(recordsById.keys == terms.mapTo(mutableSetOf(), TermDefinition::id)) {
            "Term consent snapshot must contain every current term exactly once."
        }
        require(
            terms
                .filter { definition -> definition.type == TermType.REQUIRED }
                .all { definition -> recordsById[definition.id]?.isAgreed == true },
        ) { "Every required term must be agreed before saving." }

        dataStore.edit { preferences ->
            records.forEach { record ->
                preferences[nameKey(record.id)] = record.termName
                preferences[versionKey(record.id)] = record.termVersion
                preferences[typeKey(record.id)] = record.termType.persistedValue
                preferences[agreedKey(record.id)] = record.isAgreed
                record.agreedAtEpochMillis?.let { agreedAt ->
                    preferences[agreedAtKey(record.id)] = agreedAt
                } ?: preferences.remove(agreedAtKey(record.id))
            }
        }
    }

    private fun Preferences.toFirstLaunchStatus(): FirstLaunchStatus =
        FirstLaunchStatus(
            isOnboardingCompleted = this[OnboardingCompletedKey] ?: false,
            termConsents = terms.mapNotNull { definition ->
                toTermConsentRecord(definition.id)?.let { record -> definition.id to record }
            }.toMap(),
        )

    private fun Preferences.toLegacyEntryState(): LegacyEntryState = LegacyEntryState(
        hasOnboardingKey = OnboardingCompletedKey in this,
        isOnboardingCompleted = this[OnboardingCompletedKey],
        hasAlarmGuidePendingKey = AlarmCreationGuidePendingKey in this,
        isAlarmGuidePending = this[AlarmCreationGuidePendingKey],
    )

    private fun Preferences.toInstallationEntryState(): InstallationEntryState =
        InstallationEntryState(
            policyVersion = this[EntryPolicyVersionKey],
            isOnboardingCompleted = this[InstallationOnboardingCompletedKey] ?: false,
            isAlarmCreationGuideCompleted = this[InstallationAlarmGuideCompletedKey] ?: false,
            hasLegacyRuntimeExceptionPending = this[LegacyRuntimeExceptionPendingKey] ?: false,
            isLogoutCleanupPending = this[LogoutCleanupPendingKey] ?: false,
            legacyAlarmMigrationAccountId = this[LegacyAlarmMigrationAccountIdKey],
            isLegacyAlarmMigrationComplete = this[LegacyAlarmMigrationCompleteKey] ?: false,
        )
}

private fun Preferences.toTermConsentRecord(id: TermId): TermConsentRecord? {
    val name = this[nameKey(id)] ?: return null
    val version = this[versionKey(id)] ?: return null
    val type = this[typeKey(id)]?.let(TermType::fromPersistedValue) ?: return null
    val agreed = this[agreedKey(id)] ?: return null
    return TermConsentRecord(
        id = id,
        termName = name,
        termVersion = version,
        termType = type,
        isAgreed = agreed,
        agreedAtEpochMillis = this[agreedAtKey(id)],
    )
}

internal val ThemeModeKey = stringPreferencesKey("theme.mode")
private val OnboardingCompletedKey = booleanPreferencesKey("onboarding.completed")
private val AlarmCreationGuidePendingKey = booleanPreferencesKey("onboarding.alarm_creation_guide_pending")

private val EntryPolicyVersionKey = intPreferencesKey("entry_policy_version")
private val InstallationOnboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
private val InstallationAlarmGuideCompletedKey = booleanPreferencesKey("alarm_guide_completed")
private val LegacyRuntimeExceptionPendingKey = booleanPreferencesKey("legacy_runtime_exception_pending")
private val LogoutCleanupPendingKey = booleanPreferencesKey("logout_cleanup_pending")
private val LegacyAlarmMigrationAccountIdKey = stringPreferencesKey("legacy_alarm_migration_account_id")
private val LegacyAlarmMigrationCompleteKey = booleanPreferencesKey("legacy_alarm_migration_complete")

private fun nameKey(id: TermId) = stringPreferencesKey("terms.${id.value}.name")
private fun versionKey(id: TermId) = stringPreferencesKey("terms.${id.value}.version")
private fun typeKey(id: TermId) = stringPreferencesKey("terms.${id.value}.type")
private fun agreedKey(id: TermId) = booleanPreferencesKey("terms.${id.value}.agreed")
private fun agreedAtKey(id: TermId) =
    longPreferencesKey("terms.${id.value}.agreed_at_epoch_millis")
