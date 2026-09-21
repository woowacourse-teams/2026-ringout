package com.joon.ringout.presentation.appbootstrap

import com.joon.ringout.ThemeMode
import com.joon.ringout.analytics.AnalyticsEventName
import com.joon.ringout.analytics.AnalyticsParameterName
import com.joon.ringout.analytics.AnalyticsParameterValue
import com.joon.ringout.analytics.OnboardingAnalyticsFixture
import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus
import com.joon.ringout.domain.preferences.AppBootstrapSnapshot
import com.joon.ringout.domain.preferences.AppPreferencesRepository
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
    fun `화면이 전환되어도 완료 이벤트는 설정 저장이 성공할 때까지 기다린다`() {
        val scope = testScope()
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAppPreferencesRepository(onboardingWriteGate = gate)
        val analytics = OnboardingAnalyticsFixture()
        try {
            val vm = AppBootstrapViewModel(repository, scope, analytics.recorder)
            vm.completeOnboarding(stepCount = 4)
            repository.emit(snapshot(firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true)))
            assertEquals(AppEntryDestination.Home, vm.uiState.destination)
            assertTrue(analytics.events.isEmpty())
            assertTrue(vm.uiState.isSaving)
            vm.completeOnboarding(stepCount = 4)
            assertEquals(1, repository.onboardingSaveCount)
            gate.complete(Unit)
            val event = analytics.events.single()
            assertEquals(AnalyticsEventName.TutorialComplete, event.name)
            assertEquals(AnalyticsParameterValue.Number(4), event.parameters[AnalyticsParameterName.StepCount])
            assertFalse(vm.uiState.isSaving)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `완료 상태 저장에 실패하면 이벤트를 전송하지 않고 재시도에 성공하면 한 번 전송한다`() {
        val scope = testScope()
        val repository = FakeAppPreferencesRepository(failOnboardingWrite = true)
        val analytics = OnboardingAnalyticsFixture()
        try {
            val vm = AppBootstrapViewModel(repository, scope, analytics.recorder)
            vm.completeOnboarding(stepCount = 5)
            assertTrue(analytics.events.isEmpty())
            assertEquals(1, vm.uiState.onboardingRetryToken)
            repository.failOnboardingWrite = false
            vm.completeOnboarding(stepCount = 5)
            vm.completeOnboarding(stepCount = 5)
            assertEquals(listOf(AnalyticsEventName.TutorialComplete), analytics.events.map { it.name })
        } finally {
            scope.cancel()
        }
    }

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
        assertEquals(AppEntryDestination.Home, viewModel.uiState.destination)
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

            assertEquals(AppEntryDestination.Home, viewModel.uiState.destination)
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

}

private inline fun withViewModel(
    repository: FakeAppPreferencesRepository = FakeAppPreferencesRepository(),
    block: (AppBootstrapViewModel, FakeAppPreferencesRepository) -> Unit,
) {
    val scope = testScope()
    val viewModel = AppBootstrapViewModel(
        repository = repository,
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
    var failOnboardingWrite: Boolean = false,
    private val firstThemeWriteGate: CompletableDeferred<Unit>? = null,
    var themeWriteFailuresRemaining: Int = 0,
    private val onboardingWriteGate: CompletableDeferred<Unit>? = null,
) : AppPreferencesRepository {
    private val mutableBootstrapState = MutableStateFlow(initialSnapshot)
    override val bootstrapState: Flow<AppBootstrapSnapshot> = mutableBootstrapState

    var emitThemeChanges = true
    val themeWrites = mutableListOf<ThemeMode>()
    var onboardingSaveCount = 0
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
        onboardingWriteGate?.await()
        if (failOnboardingWrite) throw IOException("write failed")
    }

    override suspend fun saveTermConsents(records: List<TermConsentRecord>) = Unit

    fun emit(snapshot: AppBootstrapSnapshot) {
        mutableBootstrapState.value = snapshot
    }
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
