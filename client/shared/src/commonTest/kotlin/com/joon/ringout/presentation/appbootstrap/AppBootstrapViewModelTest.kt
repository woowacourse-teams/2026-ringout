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
    fun `서비스 소개를 완료하면 홈 진입과 알람 생성 가이드 상태가 함께 준비된다`() = withViewModel { viewModel, _, _ ->
        viewModel.completeOnboarding()

        assertEquals(AppEntryDestination.Home, viewModel.uiState.destination)
        assertTrue(viewModel.uiState.isAlarmCreationGuidePending)
        assertFalse(viewModel.uiState.isSavingAlarmCreationGuide)
    }

    @Test
    fun `기존 사용자에게는 알람 생성 가이드를 표시하지 않는다`() = withViewModel(
        repository = FakeAppPreferencesRepository(
            initialSnapshot = snapshot(firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true)),
        ),
    ) { viewModel, repository, _ ->
        assertEquals(AppEntryDestination.Home, viewModel.uiState.destination)
        assertFalse(viewModel.uiState.isAlarmCreationGuidePending)

        viewModel.completeAlarmCreationGuide()
        assertEquals(0, repository.guideSaveCount)
    }

    @Test
    fun `가이드 종료를 저장하는 동안 중복 요청을 막고 성공하면 가이드를 종료한다`() {
        val gate = CompletableDeferred<Unit>()
        withViewModel(
            repository = FakeAppPreferencesRepository(
                initialSnapshot = snapshot(isAlarmCreationGuidePending = true),
                guideWriteGate = gate,
            ),
        ) { viewModel, repository, _ ->
            viewModel.completeAlarmCreationGuide()
            viewModel.completeAlarmCreationGuide()

            assertEquals(1, repository.guideSaveCount)
            assertTrue(viewModel.uiState.isAlarmCreationGuidePending)
            assertTrue(viewModel.uiState.isSavingAlarmCreationGuide)
            assertFalse(viewModel.uiState.isSaving)

            gate.complete(Unit)
            assertFalse(viewModel.uiState.isAlarmCreationGuidePending)
            assertFalse(viewModel.uiState.isSavingAlarmCreationGuide)
            assertFalse(viewModel.uiState.hasAlarmCreationGuideSaveFailed)

            viewModel.completeAlarmCreationGuide()
            assertEquals(1, repository.guideSaveCount)
        }
    }

    @Test
    fun `가이드 종료 저장에 실패하면 가이드를 유지하고 재시도할 수 있다`() = withViewModel(
        repository = FakeAppPreferencesRepository(
            initialSnapshot = snapshot(isAlarmCreationGuidePending = true),
            failGuideWrite = true,
        ),
    ) { viewModel, repository, _ ->
        viewModel.completeAlarmCreationGuide()

        assertTrue(viewModel.uiState.isAlarmCreationGuidePending)
        assertTrue(viewModel.uiState.hasAlarmCreationGuideSaveFailed)
        assertFalse(viewModel.uiState.isSavingAlarmCreationGuide)
        assertEquals(0, viewModel.uiState.onboardingRetryToken)

        repository.failGuideWrite = false
        viewModel.completeAlarmCreationGuide()

        assertEquals(2, repository.guideSaveCount)
        assertFalse(viewModel.uiState.isAlarmCreationGuidePending)
        assertFalse(viewModel.uiState.hasAlarmCreationGuideSaveFailed)
        assertFalse(viewModel.uiState.isSavingAlarmCreationGuide)
    }

    @Test
    fun `화면이 전환되어도 완료 이벤트는 설정 저장이 성공할 때까지 기다린다`() {
        val scope = testScope()
        val gate = CompletableDeferred<Unit>()
        val repository = FakeAppPreferencesRepository(onboardingWriteGate = gate)
        val analytics = OnboardingAnalyticsFixture()
        try {
            val vm = AppBootstrapViewModel(
                repository = repository,
                systemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Dark),
                coroutineScope = scope,
                onboardingAnalytics = analytics.recorder,
            )
            vm.completeOnboarding()
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
            val vm = AppBootstrapViewModel(
                repository = repository,
                systemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Dark),
                coroutineScope = scope,
                onboardingAnalytics = analytics.recorder,
            )
            vm.completeOnboarding()
            assertTrue(analytics.events.isEmpty())
            assertEquals(1, vm.uiState.onboardingRetryToken)
            repository.failOnboardingWrite = false
            vm.completeOnboarding()
            vm.completeOnboarding()
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
    fun `저장된 테마가 없으면 시스템 테마를 사용하고 한 번 초기화한다`() = withViewModel(
        repository = FakeAppPreferencesRepository(initialSnapshot = snapshot(themeMode = null)),
        systemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Light),
    ) { viewModel, repository, systemThemeModeReader ->
        assertTrue(viewModel.uiState.isReady)
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
        assertEquals(1, systemThemeModeReader.readCount)
        assertEquals(listOf(ThemeMode.Light), repository.themeInitializationWrites)
    }

    @Test
    fun `저장된 테마가 있으면 시스템 테마를 읽거나 초기화하지 않는다`() = withViewModel(
        repository = FakeAppPreferencesRepository(initialSnapshot = snapshot(themeMode = ThemeMode.Light)),
        systemThemeModeReader = FakeSystemThemeModeReader(ThemeMode.Dark),
    ) { viewModel, repository, systemThemeModeReader ->
        assertEquals(ThemeMode.Light, viewModel.uiState.themeMode)
        assertEquals(0, systemThemeModeReader.readCount)
        assertTrue(repository.themeInitializationWrites.isEmpty())
    }

    @Test
    fun `테마 초기화에 실패해도 준비 상태를 막지 않고 재시도한다`() = withViewModel(
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
    fun `초기화 중에는 사용자 테마 변경이 우선한다`() {
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
    fun `온보딩 저장 요청 뒤 새 스냅샷을 받아야 홈과 가이드 상태로 전환한다`() =
        withViewModel { viewModel, repository, _ ->
            repository.emitOnboardingChanges = false
            viewModel.completeOnboarding()

            assertEquals(1, repository.onboardingSaveCount)
            assertEquals(AppEntryDestination.Onboarding, viewModel.uiState.destination)
            assertFalse(viewModel.uiState.isAlarmCreationGuidePending)

            repository.emit(
                snapshot(
                    firstLaunchStatus = FirstLaunchStatus(isOnboardingCompleted = true),
                    isAlarmCreationGuidePending = true,
                ),
            )

            assertEquals(AppEntryDestination.Home, viewModel.uiState.destination)
            assertTrue(viewModel.uiState.isAlarmCreationGuidePending)
        }

    @Test
    fun `온보딩 저장에 실패하면 화면을 유지하고 재시도를 허용한다`() = withViewModel(
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
    var failOnboardingWrite: Boolean = false,
    private val firstThemeWriteGate: CompletableDeferred<Unit>? = null,
    private val firstThemeInitializationGate: CompletableDeferred<Unit>? = null,
    var themeWriteFailuresRemaining: Int = 0,
    private val onboardingWriteGate: CompletableDeferred<Unit>? = null,
    var themeInitializationFailuresRemaining: Int = 0,
    var failGuideWrite: Boolean = false,
    private val guideWriteGate: CompletableDeferred<Unit>? = null,
) : AppPreferencesRepository {
    private val mutableBootstrapState = MutableStateFlow(initialSnapshot)
    override val bootstrapState: Flow<AppBootstrapSnapshot> = mutableBootstrapState

    var emitThemeChanges = true
    var emitOnboardingChanges = true
    val themeWrites = mutableListOf<ThemeMode>()
    val themeInitializationWrites = mutableListOf<ThemeMode>()
    var onboardingSaveCount = 0
        private set
    var guideSaveCount = 0
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
        onboardingWriteGate?.await()
        if (failOnboardingWrite) throw IOException("write failed")
        val currentSnapshot = mutableBootstrapState.value
        if (emitOnboardingChanges && !currentSnapshot.firstLaunchStatus.isOnboardingCompleted) {
            emit(
                currentSnapshot.copy(
                    firstLaunchStatus = currentSnapshot.firstLaunchStatus.copy(isOnboardingCompleted = true),
                    isAlarmCreationGuidePending = true,
                ),
            )
        }
    }

    override suspend fun completeAlarmCreationGuide() {
        guideSaveCount += 1
        guideWriteGate?.await()
        if (failGuideWrite) throw IOException("guide write failed")
        emit(mutableBootstrapState.value.copy(isAlarmCreationGuidePending = false))
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
    override suspend fun completeAlarmCreationGuide() = Unit
    override suspend fun saveTermConsents(records: List<TermConsentRecord>) = Unit
}

private fun snapshot(
    themeMode: ThemeMode? = ThemeMode.Dark,
    firstLaunchStatus: FirstLaunchStatus = FirstLaunchStatus(),
    isAlarmCreationGuidePending: Boolean = false,
) = AppBootstrapSnapshot(themeMode, firstLaunchStatus, isAlarmCreationGuidePending)

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
