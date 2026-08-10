package com.joon.ringout.data.firstlaunch

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.determineAppEntryDestination
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataStoreFirstLaunchRepositoryTest {
    @Test
    fun persistsAndRestoresTheCompleteFirstLaunchSnapshot() = runBlocking {
        val directory = Files.createTempDirectory("ringout-first-launch-test").toFile()
        val preferencesFile = directory.resolve("test.preferences_pb")

        try {
            val firstScope = dataStoreScope()
            val firstRepository = DataStoreFirstLaunchRepository(
                createTestDataStore(preferencesFile, firstScope),
            )
            val agreedAt = 1_725_000_000_000L
            val records = currentTerms.map { definition ->
                TermConsentRecord(
                    id = definition.id,
                    termName = definition.name,
                    termVersion = definition.version,
                    termType = definition.type,
                    isAgreed = true,
                    agreedAtEpochMillis = agreedAt,
                )
            }

            firstRepository.markOnboardingCompleted()
            firstRepository.saveTermConsents(records)
            firstScope.cancelAndJoinChildren()

            val restoredScope = dataStoreScope()
            try {
                val restored = DataStoreFirstLaunchRepository(
                    createTestDataStore(preferencesFile, restoredScope),
                ).status.first()

                assertTrue(restored.isOnboardingCompleted)
                assertEquals(records.associateBy(TermConsentRecord::id), restored.termConsents)
                assertTrue(restored.termConsents.values.all { it.termType == TermType.REQUIRED })
                assertEquals(
                    setOf(agreedAt),
                    restored.termConsents.values.mapNotNull { it.agreedAtEpochMillis }.toSet(),
                )
            } finally {
                restoredScope.cancelAndJoinChildren()
            }
        } finally {
            preferencesFile.delete()
            directory.delete()
        }
    }

    @Test
    fun malformedRequiredConsentPreferencesNeverRouteToHome() = runBlocking {
        val directory = Files.createTempDirectory("ringout-malformed-consent-test").toFile()
        val preferencesFile = directory.resolve("test.preferences_pb")
        val scope = dataStoreScope()

        try {
            val dataStore = createTestDataStore(preferencesFile, scope)
            val repository = DataStoreFirstLaunchRepository(dataStore)
            val servicePrefix = "terms.service"
            val malformedMutations = listOf<suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit>(
                { preferences -> preferences.remove(stringPreferencesKey("$servicePrefix.name")) },
                { preferences -> preferences.remove(stringPreferencesKey("$servicePrefix.version")) },
                { preferences -> preferences.remove(stringPreferencesKey("$servicePrefix.type")) },
                { preferences -> preferences.remove(booleanPreferencesKey("$servicePrefix.agreed")) },
                {
                        preferences ->
                    preferences.remove(longPreferencesKey("$servicePrefix.agreed_at_epoch_millis"))
                },
                {
                        preferences ->
                    preferences[stringPreferencesKey("$servicePrefix.type")] = "unknown"
                },
                {
                        preferences ->
                    preferences[stringPreferencesKey("$servicePrefix.type")] = "optional"
                },
            )

            malformedMutations.forEach { mutate ->
                dataStore.edit { preferences ->
                    preferences.clear()
                    preferences[booleanPreferencesKey("onboarding.completed")] = true
                    currentTerms.forEach { definition ->
                        val prefix = "terms.${definition.id.value}"
                        preferences[stringPreferencesKey("$prefix.name")] = definition.name
                        preferences[stringPreferencesKey("$prefix.version")] = definition.version
                        preferences[stringPreferencesKey("$prefix.type")] =
                            definition.type.persistedValue
                        preferences[booleanPreferencesKey("$prefix.agreed")] = true
                        preferences[longPreferencesKey("$prefix.agreed_at_epoch_millis")] = 1_234L
                    }
                    mutate(preferences)
                }

                assertEquals(
                    AppEntryDestination.TermsAgreement,
                    determineAppEntryDestination(repository.status.first(), currentTerms),
                )
            }
        } finally {
            scope.cancelAndJoinChildren()
            preferencesFile.delete()
            directory.delete()
        }
    }
}

private fun createTestDataStore(
    file: File,
    scope: CoroutineScope,
) = DataStoreFactory.create(
    storage = FileStorage<Preferences>(
        serializer = PreferencesFileSerializer,
        produceFile = { file },
    ),
    scope = scope,
)

private fun dataStoreScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private suspend fun CoroutineScope.cancelAndJoinChildren() {
    val job = coroutineContext[Job]
    cancel()
    job?.children?.toList()?.joinAll()
}
