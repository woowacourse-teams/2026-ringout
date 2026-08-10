package com.joon.ringout.presentation.appentry

import com.joon.ringout.domain.firstlaunch.AppEntryDestination
import com.joon.ringout.domain.firstlaunch.FirstLaunchRepository
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus
import com.joon.ringout.domain.terms.TermConsentRecord
import com.joon.ringout.domain.terms.TermId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
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

class AppEntryViewModelTest {
    @Test
    fun staysLoadingUntilTheRepositoryEmitsItsFirstStatus() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val viewModel = AppEntryViewModel(
                repository = EmptyFirstLaunchRepository,
                coroutineScope = scope,
            )

            assertTrue(viewModel.uiState.isLoading)
            assertNull(viewModel.uiState.destination)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun observesTheInitialDestination() = withViewModel { viewModel, _ ->
        assertFalse(viewModel.uiState.isLoading)
        assertEquals(AppEntryDestination.Onboarding, viewModel.uiState.destination)
    }

    @Test
    fun onboardingCompletionWaitsForRepositoryStatusBeforeRouting() =
        withViewModel { viewModel, repository ->
            viewModel.completeOnboarding()

            assertEquals(1, repository.onboardingSaveCount)
            assertEquals(AppEntryDestination.Onboarding, viewModel.uiState.destination)

            repository.emit(FirstLaunchStatus(isOnboardingCompleted = true))

            assertEquals(AppEntryDestination.TermsAgreement, viewModel.uiState.destination)
        }

    @Test
    fun onboardingWriteFailureKeepsTheScreenAndEnablesRetry() =
        withViewModel(repository = FakeFirstLaunchRepository(failOnboardingWrite = true)) {
                viewModel,
                repository,
            ->
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
            repository.emit(FirstLaunchStatus(isOnboardingCompleted = true))

            viewModel.completeTermsAgreement(setOf(TermId.Service, TermId.Privacy))

            val records = requireNotNull(repository.savedRecords)
            assertEquals(setOf(TermId.Service, TermId.Privacy), records.mapTo(mutableSetOf()) { it.id })
            assertTrue(records.all { record -> record.termVersion == "1" })
            assertTrue(records.all(TermConsentRecord::isAgreed))
            assertEquals(setOf(9_876L), records.mapNotNull { it.agreedAtEpochMillis }.toSet())
            assertEquals(AppEntryDestination.TermsAgreement, viewModel.uiState.destination)
        }

    @Test
    fun termWriteFailureKeepsTheScreenAndAllowsRetry() =
        withViewModel(repository = FakeFirstLaunchRepository(failTermsWrite = true)) {
                viewModel,
                repository,
            ->
            repository.emit(FirstLaunchStatus(isOnboardingCompleted = true))

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
            repository = FakeFirstLaunchRepository(termWriteGate = writeGate),
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
    repository: FakeFirstLaunchRepository = FakeFirstLaunchRepository(),
    clock: Clock = FixedClock(1L),
    block: (AppEntryViewModel, FakeFirstLaunchRepository) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val viewModel = AppEntryViewModel(
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

private class FakeFirstLaunchRepository(
    initialStatus: FirstLaunchStatus = FirstLaunchStatus(),
    private val failOnboardingWrite: Boolean = false,
    private val failTermsWrite: Boolean = false,
    private val termWriteGate: CompletableDeferred<Unit>? = null,
) : FirstLaunchRepository {
    private val mutableStatus = MutableStateFlow(initialStatus)
    override val status: Flow<FirstLaunchStatus> = mutableStatus

    var onboardingSaveCount = 0
        private set
    var savedRecords: List<TermConsentRecord>? = null
        private set
    var termSaveCount = 0
        private set

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

    fun emit(status: FirstLaunchStatus) {
        mutableStatus.value = status
    }
}

private class FixedClock(
    private val epochMillis: Long,
) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(epochMillis)
}

private object EmptyFirstLaunchRepository : FirstLaunchRepository {
    override val status: Flow<FirstLaunchStatus> = emptyFlow()

    override suspend fun markOnboardingCompleted() = Unit

    override suspend fun saveTermConsents(records: List<TermConsentRecord>) = Unit
}
