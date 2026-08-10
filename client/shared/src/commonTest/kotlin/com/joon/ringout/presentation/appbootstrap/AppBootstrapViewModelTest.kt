package com.joon.ringout.presentation.appbootstrap

import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus
import com.joon.ringout.domain.preferences.AppBootstrapSnapshot
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import okio.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AppBootstrapViewModelTest {
    @Test
    fun staysNotReadyUntilTheRepositoryEmitsItsFirstSnapshot() {
        val scope = testScope()
        try {
            val viewModel = AppBootstrapViewModel(
                repository = EmptyAppPreferencesRepository,
                coroutineScope = scope,
            )

            assertFalse(viewModel.uiState.isReady)
            assertEquals(ThemeMode.Dark, viewModel.uiState.themeMode)
            assertNull(viewModel.uiState.destination)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun firstSnapshotMakesThemeAndDestinationReadyTogether() = withViewModel(
        repository = FakeAppPreferencesRepository(
            initialSnapshot = snapshot(
                themeMode = ThemeMode.Light,
                firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true),
            ),
        ),
    ) { viewModel, _ ->
        assertTrue(viewModel.uiState.isReady)
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
        assertEquals(AppEntryDestination.TermsAgreement, viewModel.uiState.destination)
    }

    @Test
    fun themeChangeWaitsForRepositoryEmission() = withViewModel { viewModel, repository ->
        repository.emitThemeChanges = false

        viewModel.setThemeMode(ThemeMode.Light)

        assertEquals(listOf(ThemeMode.Light), repository.themeWrites)
        assertEquals(ThemeMode.Dark, viewModel.uiState.themeMode)

        repository.emit(snapshot(themeMode = ThemeMode.Light))
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
    }

    @Test
    fun unchangedThemeDoesNotWrite() = withViewModel { viewModel, repository ->
        viewModel.setThemeMode(ThemeMode.Dark)

        assertTrue(repository.themeWrites.isEmpty())
    }

    @Test
    fun consecutiveThemeChangesPersistTheLastRequest() {
        val firstWriteGate = CompletableDeferred<Unit>()
        withViewModel(
            repository = FakeAppPreferencesRepository(firstThemeWriteGate = firstWriteGate),
        ) { viewModel, repository ->
            viewModel.setThemeMode(ThemeMode.Light)
            viewModel.setThemeMode(ThemeMode.Dark)

            firstWriteGate.complete(Unit)

            assertEquals(listOf(ThemeMode.Light, ThemeMode.Dark), repository.themeWrites)
            assertEquals(ThemeMode.Dark, viewModel.uiState.themeMode)
        }
    }

    @Test
    fun failedThemeWriteCanBeRetried() = withViewModel(
        repository = FakeAppPreferencesRepository(themeWriteFailuresRemaining = 1),
    ) { viewModel, repository ->
        viewModel.setThemeMode(ThemeMode.Light)
        viewModel.setThemeMode(ThemeMode.Light)

        assertEquals(listOf(ThemeMode.Light, ThemeMode.Light), repository.themeWrites)
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
    }

    @Test
    fun onboardingCompletionWaitsForRepositorySnapshotBeforeRouting() =
        withViewModel { viewModel, repository ->
            viewModel.completeOnboarding()

            assertEquals(1, repository.onboardingSaveCount)
            assertEquals(AppEntryDestination.Onboarding, viewModel.uiState.destination)

            repository.emit(
                snapshot(firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true)),
            )

            assertEquals(AppEntryDestination.TermsAgreement, viewModel.uiState.destination)
        }

    @Test
    fun onboardingWriteFailureKeepsTheScreenAndEnablesRetry() = withViewModel(
        repository = FakeAppPreferencesRepository(failOnboardingWrite = true),
    ) { viewModel, repository ->
        viewModel.completeOnboarding()

        assertEquals(AppEntryDestination.Onboarding, viewModel.uiState.destination)
        assertFalse(viewModel.uiState.isSaving)
        assertEquals(1, viewModel.uiState.onboardingRetryToken)

        viewModel.completeOnboarding()
        assertEquals(2, repository.onboardingSaveCount)
    }

    @Test
    fun termCompletionCreatesAFullSnapshotWithOneClockValue() =
        withViewModel(clock = FixedClock(9_876L)) { viewModel, repository ->
            repository.emit(
                snapshot(firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true)),
            )

            viewModel.completeTermsAgreement(setOf(TermId.Service, TermId.Privacy))

            val records = requireNotNull(repository.savedRecords)
            assertEquals(setOf(TermId.Service, TermId.Privacy), records.mapTo(mutableSetOf()) { it.id })
            assertTrue(records.all { record -> record.termVersion == "1" })
            assertTrue(records.all(TermConsentRecord::isAgreed))
            assertEquals(setOf(9_876L), records.mapNotNull { it.agreedAtEpochMillis }.toSet())
            assertEquals(AppEntryDestination.TermsAgreement, viewModel.uiState.destination)
        }

    @Test
    fun termWriteFailureKeepsTheScreenAndAllowsRetry() = withViewModel(
        repository = FakeAppPreferencesRepository(failTermsWrite = true),
    ) { viewModel, repository ->
        repository.emit(
            snapshot(firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true)),
        )

        viewModel.completeTermsAgreement(setOf(TermId.Service, TermId.Privacy))

        assertEquals(AppEntryDestination.TermsAgreement, viewModel.uiState.destination)
        assertFalse(viewModel.uiState.isSaving)
        assertEquals(1, repository.termSaveCount)

        viewModel.completeTermsAgreement(setOf(TermId.Service, TermId.Privacy))
        assertEquals(2, repository.termSaveCount)
    }

    @Test
    fun ignoresDuplicateTermCompletionWhileTheFirstWriteIsPending() {
        val writeGate = CompletableDeferred<Unit>()
        withViewModel(
            repository = FakeAppPreferencesRepository(termWriteGate = writeGate),
        ) { viewModel, repository ->
            val agreedIds = setOf(TermId.Service, TermId.Privacy)

            viewModel.completeTermsAgreement(agreedIds)
            viewModel.completeTermsAgreement(agreedIds)

            assertTrue(viewModel.uiState.isSaving)
            assertEquals(1, repository.termSaveCount)

            writeGate.complete(Unit)
            assertFalse(viewModel.uiState.isSaving)
        }
    }

    @Test
    fun incompleteRequiredAgreementDoesNotWrite() = withViewModel { viewModel, repository ->
        viewModel.completeTermsAgreement(setOf(TermId.Service))

        assertNull(repository.savedRecords)
    }
}

private inline fun withViewModel(
    repository: FakeAppPreferencesRepository = FakeAppPreferencesRepository(),
    clock: Clock = FixedClock(1L),
    block: (AppBootstrapViewModel, FakeAppPreferencesRepository) -> Unit,
) {
    val scope = testScope()
    val viewModel = AppBootstrapViewModel(
        repository = repository,
        clock = clock,
        coroutineScope = scope,
    )
    try {
        block(viewModel, repository)
    } finally {
        scope.cancel()
    }
}

private class FakeAppPreferencesRepository(
    initialSnapshot: AppBootstrapSnapshot = snapshot(),
    private val failOnboardingWrite: Boolean = false,
    private val failTermsWrite: Boolean = false,
    private val termWriteGate: CompletableDeferred<Unit>? = null,
    private val firstThemeWriteGate: CompletableDeferred<Unit>? = null,
    var themeWriteFailuresRemaining: Int = 0,
) : AppPreferencesRepository {
    private val mutableBootstrapState = MutableStateFlow(initialSnapshot)
    override val bootstrapState: Flow<AppBootstrapSnapshot> = mutableBootstrapState

    var emitThemeChanges = true
    val themeWrites = mutableListOf<ThemeMode>()
    var onboardingSaveCount = 0
        private set
    var savedRecords: List<TermConsentRecord>? = null
        private set
    var termSaveCount = 0
        private set

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        themeWrites += themeMode
        if (themeWrites.size == 1) firstThemeWriteGate?.await()
        if (themeWriteFailuresRemaining > 0) {
            themeWriteFailuresRemaining -= 1
            throw IOException("write failed")
        }
        if (emitThemeChanges) {
            emit(mutableBootstrapState.value.copy(themeMode = themeMode))
        }
    }

    override suspend fun markOnboardingCompleted() {
        onboardingSaveCount += 1
        if (failOnboardingWrite) throw IOException("write failed")
    }

    override suspend fun saveTermConsents(records: List<TermConsentRecord>) {
        termSaveCount += 1
        termWriteGate?.await()
        if (failTermsWrite) throw IOException("write failed")
        savedRecords = records
    }

    fun emit(snapshot: AppBootstrapSnapshot) {
        mutableBootstrapState.value = snapshot
    }
}

private class FixedClock(
    private val epochMillis: Long,
) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis)
}

private object EmptyAppPreferencesRepository : AppPreferencesRepository {
    override val bootstrapState: Flow<AppBootstrapSnapshot> = emptyFlow()
    override suspend fun setThemeMode(themeMode: ThemeMode) = Unit
    override suspend fun markOnboardingCompleted() = Unit
    override suspend fun saveTermConsents(records: List<TermConsentRecord>) = Unit
}

private fun snapshot(
    themeMode: ThemeMode = ThemeMode.Dark,
    firstLaunchStatus: FirstLaunchStatus = FirstLaunchStatus(),
) = AppBootstrapSnapshot(themeMode, firstLaunchStatus)

private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
