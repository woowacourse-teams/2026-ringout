package com.joon.ringout.data.preferences

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.FileStorage
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.firstlaunch.EntryPolicyVersion
import com.joon.ringout.domain.preferences.InstallationEntryState
import com.joon.ringout.domain.preferences.LegacyEntryState
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.determineAppEntryDestination
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermType
import com.joon.ringout.domain.terms.currentTerms
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreAppPreferencesRepositoryTest {
    @Test
    fun `새 설치는 소개와 가이드 완료를 설치 저장소에만 기록한다`() = withRepository(
        prefix = "ringout-new-install-test",
    ) { dataStore, installationDataStore, repository ->
        assertFalse(repository.bootstrapState.first().isAlarmCreationGuidePending)
        repository.initializeEntryPolicy(AuthSessionState.Unauthenticated, false)
        val initial = repository.bootstrapState.first()
        assertFalse(initial.installationEntryState!!.isOnboardingCompleted)
        assertTrue(initial.isAlarmCreationGuidePending)

        repository.markOnboardingCompleted()
        repository.completeAlarmCreationGuide()

        assertEquals(
            InstallationEntryState(
                policyVersion = EntryPolicyVersion,
                isOnboardingCompleted = true,
                isAlarmCreationGuideCompleted = true,
            ),
            repository.bootstrapState.first().installationEntryState,
        )
        assertTrue(dataStore.data.first().asMap().isEmpty())
        assertEquals(true, installationDataStore.data.first()[booleanPreferencesKey("onboarding_completed")])
        assertEquals(true, installationDataStore.data.first()[booleanPreferencesKey("alarm_guide_completed")])
    }

    @Test
    fun `종료한 가이드는 소개 완료와 정책 초기화를 반복해도 다시 시작하지 않는다`() = withRepository(
        prefix = "ringout-completed-guide-test",
    ) { _, _, repository ->
        repository.initializeEntryPolicy(AuthSessionState.Unauthenticated, false)
        repository.markOnboardingCompleted()
        repository.completeAlarmCreationGuide()
        repository.markOnboardingCompleted()
        repository.initializeEntryPolicy(AuthSessionState.Unauthenticated, true)

        assertFalse(repository.bootstrapState.first().isAlarmCreationGuidePending)
        assertTrue(repository.bootstrapState.first().installationEntryState!!.isOnboardingCompleted)
    }

    @Test
    fun `레거시 키의 부재와 거짓 값을 구분해서 읽는다`() = withRepository(
        prefix = "ringout-legacy-presence-test",
    ) { dataStore, _, repository ->
        assertEquals(LegacyEntryState(), repository.bootstrapState.first().legacyEntryState)
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("onboarding.completed")] = false
            preferences[booleanPreferencesKey("onboarding.alarm_creation_guide_pending")] = false
        }

        assertEquals(
            LegacyEntryState(
                hasOnboardingKey = true,
                isOnboardingCompleted = false,
                hasAlarmGuidePendingKey = true,
                isAlarmGuidePending = false,
            ),
            repository.bootstrapState.first().legacyEntryState,
        )
    }

    @Test
    fun `정책 버전과 마이그레이션 결과를 하나의 설치 스냅샷으로 저장한다`() = withRepository(
        prefix = "ringout-atomic-migration-test",
    ) { dataStore, installationDataStore, repository ->
        dataStore.edit { it[booleanPreferencesKey("onboarding.completed")] = false }
        coroutineScope {
            val initialRead = CompletableDeferred<Unit>()
            val snapshots = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    installationDataStore.data.onEach { initialRead.complete(Unit) }.take(2).toList()
                }
            }
            initialRead.await()
            repository.initializeEntryPolicy(AuthSessionState.Unauthenticated, false)
            val writes = snapshots.await()

            assertTrue(writes.first().asMap().isEmpty())
            val migrated = writes.last()
            assertEquals(EntryPolicyVersion, migrated[intPreferencesKey("entry_policy_version")])
            assertEquals(false, migrated[booleanPreferencesKey("onboarding_completed")])
            assertEquals(false, migrated[booleanPreferencesKey("alarm_guide_completed")])
            assertEquals(true, migrated[booleanPreferencesKey("legacy_runtime_exception_pending")])
            assertEquals(false, migrated[booleanPreferencesKey("logout_cleanup_pending")])
        }
    }

    @Test
    fun `인증 복원이 끝나기 전에는 정책 버전을 기록하지 않는다`() = withRepository(
        prefix = "ringout-restoring-policy-test",
    ) { _, installationDataStore, repository ->
        assertFailsWith<IllegalArgumentException> {
            repository.initializeEntryPolicy(AuthSessionState.Restoring, false)
        }

        assertTrue(installationDataStore.data.first().asMap().isEmpty())
    }

    @Test
    fun `인증된 기존 설치는 레거시 가이드 진행 여부를 보존한다`() = withRepository(
        prefix = "ringout-legacy-guide-test",
    ) { dataStore, _, repository ->
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("onboarding.completed")] = true
            preferences[booleanPreferencesKey("onboarding.alarm_creation_guide_pending")] = true
        }
        repository.initializeEntryPolicy(AuthSessionState.Authenticated, false)

        val snapshot = repository.bootstrapState.first()
        assertTrue(snapshot.installationEntryState!!.isOnboardingCompleted)
        assertTrue(snapshot.isAlarmCreationGuidePending)
        assertEquals(
            AppEntryDestination.Guide,
            determineAppEntryDestination(snapshot.installationEntryState, AuthSessionState.Authenticated),
        )
    }

    @Test
    fun `이미 소개를 마친 인증된 기존 설치는 가이드 키가 없어도 홈으로 진입한다`() = withRepository(
        prefix = "ringout-legacy-home-test",
    ) { dataStore, _, repository ->
        dataStore.edit { it[booleanPreferencesKey("onboarding.completed")] = true }
        repository.initializeEntryPolicy(AuthSessionState.Authenticated, false)

        val installation = repository.bootstrapState.first().installationEntryState!!
        assertTrue(installation.isAlarmCreationGuideCompleted)
        assertEquals(AppEntryDestination.Home, determineAppEntryDestination(installation, AuthSessionState.Authenticated))
    }

    @Test
    fun `로그아웃 복구 상태는 정책 초기화와 완료 플래그 변경에도 유지된다`() = withRepository(
        prefix = "ringout-logout-recovery-test",
    ) { dataStore, _, repository ->
        repository.setThemeMode(ThemeMode.Dark)
        repository.saveTermConsents(consentRecords())
        val accountBefore = dataStore.data.first()
        repository.setLogoutCleanupPending(true)
        repository.initializeEntryPolicy(AuthSessionState.Unauthenticated, true)
        repository.markOnboardingCompleted()
        repository.completeAlarmCreationGuide()
        repository.setLegacyRuntimeExceptionPending(false)

        val pending = repository.bootstrapState.first().installationEntryState!!
        assertTrue(pending.isLogoutCleanupPending)
        assertFalse(pending.hasLegacyRuntimeExceptionPending)
        assertEquals(AppEntryDestination.LogoutCleaning, determineAppEntryDestination(pending, AuthSessionState.Authenticated))
        repository.setLogoutCleanupPending(false)
        assertFalse(repository.bootstrapState.first().installationEntryState!!.isLogoutCleanupPending)
        assertEquals(accountBefore, dataStore.data.first())
    }

    @Test
    fun `마이그레이션을 반복해도 기존 설치 상태와 계정 데이터를 덮어쓰지 않는다`() = withRepository(
        prefix = "ringout-migration-idempotence-test",
    ) { dataStore, installationDataStore, repository ->
        repository.setThemeMode(ThemeMode.Light)
        repository.saveTermConsents(consentRecords())
        dataStore.edit { it[booleanPreferencesKey("onboarding.completed")] = true }
        val accountBefore = dataStore.data.first()
        repository.initializeEntryPolicy(AuthSessionState.Authenticated, false)
        repository.setLogoutCleanupPending(true)
        repository.setLegacyRuntimeExceptionPending(true)
        val installationBefore = installationDataStore.data.first()

        repository.initializeEntryPolicy(AuthSessionState.Unauthenticated, false)

        assertEquals(accountBefore, dataStore.data.first())
        assertEquals(installationBefore, installationDataStore.data.first())
    }

    @Test
    fun `저장되지 않았거나 지원하지 않는 테마 값은 해결되지 않은 상태로 남는다`() = withRepository(
        prefix = "ringout-theme-fallback-test",
    ) { dataStore, _, repository ->
        assertEquals(null, repository.bootstrapState.first().themeMode)

        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("theme.mode")] = "system"
        }

        assertEquals(null, repository.bootstrapState.first().themeMode)
    }

    @Test
    fun `저장된 테마 값이 없거나 지원하지 않으면 초기 테마를 저장한다`() = withRepository(
        prefix = "ringout-theme-initialization-test",
    ) { dataStore, _, repository ->
        repository.initializeThemeModeIfMissing(ThemeMode.Light)
        assertEquals(ThemeMode.Light, repository.bootstrapState.first().themeMode)

        dataStore.edit { preferences ->
            preferences[stringPreferencesKey("theme.mode")] = "system"
        }
        repository.initializeThemeModeIfMissing(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, repository.bootstrapState.first().themeMode)
    }

    @Test
    fun `유효한 사용자 테마는 초기화로 덮어쓰지 않는다`() = withRepository(
        prefix = "ringout-theme-initialization-race-test",
    ) { _, _, repository ->
        repository.setThemeMode(ThemeMode.Light)
        repository.initializeThemeModeIfMissing(ThemeMode.Dark)

        assertEquals(ThemeMode.Light, repository.bootstrapState.first().themeMode)
    }

    @Test
    fun `테마와 약관 및 설치 완료 상태를 앱 재실행 후 복원한다`() = runBlocking {
        val directory = Files.createTempDirectory("ringout-app-preferences-test").toFile()
        val preferencesFile = directory.resolve("test.preferences_pb")
        val installationFile = directory.resolve("installation.preferences_pb")

        try {
            val firstScope = dataStoreScope()
            val firstRepository = DataStoreAppPreferencesRepository(
                createTestDataStore(preferencesFile, firstScope),
                createTestDataStore(installationFile, firstScope),
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

            firstRepository.setThemeMode(ThemeMode.Light)
            firstRepository.initializeEntryPolicy(AuthSessionState.Unauthenticated, false)
            firstRepository.markOnboardingCompleted()
            firstRepository.completeAlarmCreationGuide()
            firstRepository.saveTermConsents(records)
            firstScope.cancelAndJoinChildren()

            val restoredScope = dataStoreScope()
            try {
                val restored = DataStoreAppPreferencesRepository(
                    createTestDataStore(preferencesFile, restoredScope),
                    createTestDataStore(installationFile, restoredScope),
                ).bootstrapState.first()

                assertEquals(ThemeMode.Light, restored.themeMode)
                assertTrue(restored.installationEntryState!!.isOnboardingCompleted)
                assertFalse(restored.isAlarmCreationGuidePending)
                assertTrue(restored.installationEntryState.isAlarmCreationGuideCompleted)
                assertEquals(
                    records.associateBy(TermConsentRecord::id),
                    restored.firstLaunchStatus.termConsents,
                )
                assertTrue(
                    restored.firstLaunchStatus.termConsents.values.all {
                        it.termType == TermType.REQUIRED
                    },
                )
                assertEquals(
                    setOf(agreedAt),
                    restored.firstLaunchStatus.termConsents.values
                        .mapNotNull { it.agreedAtEpochMillis }
                        .toSet(),
                )
            } finally {
                restoredScope.cancelAndJoinChildren()
            }
        } finally {
            preferencesFile.delete()
            installationFile.delete()
            directory.delete()
        }
    }

    @Test
    fun `밝은 테마 다음에 어두운 테마를 저장하면 마지막 값을 읽는다`() = withRepository(
        prefix = "ringout-theme-toggle-test",
    ) { _, _, repository ->
        repository.setThemeMode(ThemeMode.Light)
        repository.setThemeMode(ThemeMode.Dark)

        assertEquals(ThemeMode.Dark, repository.bootstrapState.first().themeMode)
    }

    @Test
    fun `손상된 약관 데이터는 완료된 설치의 홈 진입을 차단하지 않는다`() = withRepository(
        prefix = "ringout-malformed-consent-test",
    ) { dataStore, _, repository ->
        repository.initializeEntryPolicy(AuthSessionState.Authenticated, false)
        repository.completeAlarmCreationGuide()
        val servicePrefix = "terms.service"
        val malformedMutations = listOf<suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit>(
            { preferences -> preferences.remove(stringPreferencesKey("$servicePrefix.name")) },
            { preferences -> preferences.remove(stringPreferencesKey("$servicePrefix.version")) },
            { preferences -> preferences.remove(stringPreferencesKey("$servicePrefix.type")) },
            { preferences -> preferences.remove(booleanPreferencesKey("$servicePrefix.agreed")) },
            { preferences ->
                preferences.remove(longPreferencesKey("$servicePrefix.agreed_at_epoch_millis"))
            },
            { preferences ->
                preferences[stringPreferencesKey("$servicePrefix.type")] = "unknown"
            },
            { preferences ->
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
                AppEntryDestination.Home,
                determineAppEntryDestination(
                    repository.bootstrapState.first().installationEntryState!!,
                    AuthSessionState.Authenticated,
                ),
            )
        }
    }
}

private fun withRepository(
    prefix: String,
    block: suspend (
        androidx.datastore.core.DataStore<Preferences>,
        androidx.datastore.core.DataStore<Preferences>,
        DataStoreAppPreferencesRepository,
    ) -> Unit,
) = runBlocking {
    val directory = Files.createTempDirectory(prefix).toFile()
    val preferencesFile = directory.resolve("test.preferences_pb")
    val installationFile = directory.resolve("installation.preferences_pb")
    val scope = dataStoreScope()
    try {
        val dataStore = createTestDataStore(preferencesFile, scope)
        val installationDataStore = createTestDataStore(installationFile, scope)
        block(dataStore, installationDataStore, DataStoreAppPreferencesRepository(dataStore, installationDataStore))
    } finally {
        scope.cancelAndJoinChildren()
        preferencesFile.delete()
        installationFile.delete()
        directory.delete()
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

private fun consentRecords() = currentTerms.map { definition ->
    TermConsentRecord(
        id = definition.id,
        termName = definition.name,
        termVersion = definition.version,
        termType = definition.type,
        isAgreed = true,
        agreedAtEpochMillis = 1_725_000_000_000L,
    )
}
