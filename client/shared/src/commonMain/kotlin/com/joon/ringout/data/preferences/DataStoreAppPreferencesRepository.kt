package com.joon.ringout.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus
import com.joon.ringout.domain.preferences.AppBootstrapSnapshot
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermDefinition
import com.joon.ringout.domain.terms.TermId
import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStoreAppPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val terms: List<TermDefinition> = currentTerms,
) : AppPreferencesRepository {
    override val bootstrapState: Flow<AppBootstrapSnapshot> = dataStore.data.map { preferences ->
        AppBootstrapSnapshot(
            themeMode = ThemeMode.fromPersistedValue(preferences[ThemeModeKey]),
            firstLaunchStatus = preferences.toFirstLaunchStatus(),
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
        dataStore.edit { preferences ->
            preferences[OnboardingCompletedKey] = true
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

private fun nameKey(id: TermId) = stringPreferencesKey("terms.${id.value}.name")
private fun versionKey(id: TermId) = stringPreferencesKey("terms.${id.value}.version")
private fun typeKey(id: TermId) = stringPreferencesKey("terms.${id.value}.type")
private fun agreedKey(id: TermId) = booleanPreferencesKey("terms.${id.value}.agreed")
private fun agreedAtKey(id: TermId) =
    longPreferencesKey("terms.${id.value}.agreed_at_epoch_millis")
