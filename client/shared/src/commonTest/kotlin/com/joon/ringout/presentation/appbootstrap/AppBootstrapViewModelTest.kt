package com.joon.ringout.presentation.appbootstrap

import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus
import com.joon.ringout.domain.preferences.AppBootstrapSnapshot
import com.joon.ringout.domain.preferences.AppPreferencesRepository
import com.joon.ringout.domain.preferences.SystemThemeModeReader
import com.joon.ringout.domain.terms.TermConsentRecord
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

class AppBootstrapViewModelTest {
    @Test
    fun staysNotReadyUntilTheRepositoryEmitsItsFirstSnapshot() {
        val scope = testScope()
        try {
            val viewModel = AppBootstrapViewModel(
                repository = EmptyAppPreferencesRepository,
                systemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Dark),
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
    ) { viewModel, _, _ ->
        assertTrue(viewModel.uiState.isReady)
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
        assertEquals(AppEntryDestination.Home, viewModel.uiState.destination)
    }

    @Test
    fun missingThemeUsesSystemThemeAndInitializesItOnce() = withViewModel(
        repository = FakeAppPreferencesRepository(initialSnapshot = snapshot(themeMode = null)),
        systemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Light),
    ) { viewModel, repository, systemThemeModeReader ->
        assertTrue(viewModel.uiState.isReady)
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
        assertEquals(1, systemThemeModeReader.readCount)
        assertEquals(listOf(ThemeMode.Light), repository.themeInitializationWrites)
    }

    @Test
    fun storedThemeSkipsSystemThemeReadAndInitialization() = withViewModel(
        repository = FakeAppPreferencesRepository(initialSnapshot = snapshot(themeMode = ThemeMode.Light)),
        systemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Dark),
    ) { viewModel, repository, systemThemeModeReader ->
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
        assertEquals(0, systemThemeModeReader.readCount)
        assertTrue(repository.themeInitializationWrites.isEmpty())
    }

    @Test
    fun failedThemeInitializationRetriesWithoutBlockingReadyState() = withViewModel(
        repository = FakeAppPreferencesRepository(
            initialSnapshot = snapshot(themeMode = null),
            themeInitializationFailuresRemaining = 1,
        ),
        themeInitializationRetryDelayMillis = 0,
    ) { viewModel, repository, _ ->
        assertTrue(viewModel.uiState.isReady)
        assertEquals(ThemeMode.Dark, viewModel.uiState.themeMode)
        assertEquals(listOf(ThemeMode.Dark, ThemeMode.Dark), repository.themeInitializationWrites)
    }

    @Test
    fun userThemeChangeWinsWhenInitializationIsAlreadyWriting() {
        val initializationGate = CompletableDeferred<Unit>()
        withViewModel(
            repository = FakeAppPreferencesRepository(
                initialSnapshot = snapshot(themeMode = null),
                firstThemeInitializationGate = initializationGate,
            ),
        ) { viewModel, repository, _ ->
            viewModel.setThemeMode(ThemeMode.Light)
            initializationGate.complete(Unit)

            assertEquals(listOf(ThemeMode.Dark), repository.themeInitializationWrites)
            assertEquals(listOf(ThemeMode.Light), repository.themeWrites)
            assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
        }
    }

    @Test
    fun themeChangeWaitsForRepositoryEmission() = withViewModel { viewModel, repository, _ ->
        repository.emitThemeChanges = false

        viewModel.setThemeMode(ThemeMode.Light)

        assertEquals(listOf(ThemeMode.Light), repository.themeWrites)
        assertEquals(ThemeMode.Dark, viewModel.uiState.themeMode)

        repository.emit(snapshot(themeMode = ThemeMode.Light))
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
    }

    @Test
    fun unchangedThemeDoesNotWrite() = withViewModel { viewModel, repository, _ ->
        viewModel.setThemeMode(ThemeMode.Dark)

        assertTrue(repository.themeWrites.isEmpty())
    }

    @Test
    fun consecutiveThemeChangesPersistTheLastRequest() {
        val firstWriteGate = CompletableDeferred<Unit>()
        withViewModel(
            repository = FakeAppPreferencesRepository(firstThemeWriteGate = firstWriteGate),
        ) { viewModel, repository, _ ->
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
    ) { viewModel, repository, _ ->
        viewModel.setThemeMode(ThemeMode.Light)
        viewModel.setThemeMode(ThemeMode.Light)

        assertEquals(listOf(ThemeMode.Light, ThemeMode.Light), repository.themeWrites)
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
    }

    @Test
    fun onboardingCompletionWaitsForRepositorySnapshotBeforeRouting() =
        withViewModel { viewModel, repository, _ ->
            viewModel.completeOnboarding()

            assertEquals(1, repository.onboardingSaveCount)
            assertEquals(AppEntryDestination.Onboarding, viewModel.uiState.destination)

            repository.emit(
                snapshot(firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true)),
            )

            assertEquals(AppEntryDestination.Home, viewModel.uiState.destination)
        }

    @Test
    fun onboardingWriteFailureKeepsTheScreenAndEnablesRetry() = withViewModel(
        repository = FakeAppPreferencesRepository(failOnboardingWrite = true),
    ) { viewModel, repository, _ ->
        viewModel.completeOnboarding()

        assertEquals(AppEntryDestination.Onboarding, viewModel.uiState.destination)
        assertFalse(viewModel.uiState.isSaving)
        assertEquals(1, viewModel.uiState.onboardingRetryToken)

        viewModel.completeOnboarding()
        assertEquals(2, repository.onboardingSaveCount)
    }

}

private inline fun withViewModel(
    repository: FakeAppPreferencesRepository = FakeAppPreferencesRepository(),
    systemThemeModeReader: FakeSystemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Dark),
    themeInitializationRetryDelayMillis: Long = 0,
    block: (
        AppBootstrapViewModel,
        FakeAppPreferencesRepository,
        FakeSystemThemeModeReader,
    ) -> Unit,
) {
    val scope = testScope()
    val viewModel = AppBootstrapViewModel(
        repository = repository,
        systemThemeModeReader = systemThemeModeReader,
        coroutineScope = scope,
        themeInitializationRetryDelayMillis = themeInitializationRetryDelayMillis,
    )
    try {
        block(viewModel, repository, systemThemeModeReader)
    } finally {
        scope.cancel()
    }
}

private class FakeAppPreferencesRepository(
    initialSnapshot: AppBootstrapSnapshot = snapshot(),
    private val failOnboardingWrite: Boolean = false,
    private val firstThemeWriteGate: CompletableDeferred<Unit>? = null,
    private val firstThemeInitializationGate: CompletableDeferred<Unit>? = null,
    var themeWriteFailuresRemaining: Int = 0,
    var themeInitializationFailuresRemaining: Int = 0,
) : AppPreferencesRepository {
    private val mutableBootstrapState = MutableStateFlow(initialSnapshot)
    override val bootstrapState: Flow<AppBootstrapSnapshot> = mutableBootstrapState

    var emitThemeChanges = true
    val themeWrites = mutableListOf<ThemeMode>()
    val themeInitializationWrites = mutableListOf<ThemeMode>()
    var onboardingSaveCount = 0
        private set

    override suspend fun initializeThemeModeIfMissing(themeMode: ThemeMode) {
        themeInitializationWrites += themeMode
        if (themeInitializationWrites.size == 1) firstThemeInitializationGate?.await()
        if (themeInitializationFailuresRemaining > 0) {
            themeInitializationFailuresRemaining -= 1
            throw IOException("initialization failed")
        }
        if (mutableBootstrapState.value.themeMode == null) {
            emit(mutableBootstrapState.value.copy(themeMode = themeMode))
        }
    }

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

    override suspend fun saveTermConsents(records: List<TermConsentRecord>) = Unit

    fun emit(snapshot: AppBootstrapSnapshot) {
        mutableBootstrapState.value = snapshot
    }
}

private object EmptyAppPreferencesRepository : AppPreferencesRepository {
    override val bootstrapState: Flow<AppBootstrapSnapshot> = emptyFlow()
    override suspend fun initializeThemeModeIfMissing(themeMode: ThemeMode) = Unit
    override suspend fun setThemeMode(themeMode: ThemeMode) = Unit
    override suspend fun markOnboardingCompleted() = Unit
    override suspend fun saveTermConsents(records: List<TermConsentRecord>) = Unit
}

private fun snapshot(
    themeMode: ThemeMode? = ThemeMode.Dark,
    firstLaunchStatus: FirstLaunchStatus = FirstLaunchStatus(),
) = AppBootstrapSnapshot(themeMode, firstLaunchStatus)

private class FakeSystemThemeModeReader(
    private val themeMode: ThemeMode,
) : SystemThemeModeReader {
    var readCount = 0
        private set

    override fun read(): ThemeMode {
        readCount += 1
        return themeMode
    }
}

private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
