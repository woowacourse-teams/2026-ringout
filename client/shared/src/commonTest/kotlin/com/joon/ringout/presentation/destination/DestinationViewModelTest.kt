package com.joon.ringout.presentation.destination

import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DestinationViewModelTest {
    @Test
    fun successfulNewDestinationSaveRecordsCreationAfterTheRepositoryReturns() =
        withViewModel { viewModel, repository, recorder ->
            repository.saveResult = NewDestination.copy(id = 42L)
            repository.order = recorder.order

            viewModel.save(
                destination = NewDestination,
                requestId = 7L,
                loginState = AnalyticsLoginState.LoggedIn,
            )

            assertEquals(
                listOf(DestinationCreatedRecord(42L, AnalyticsLoginState.LoggedIn)),
                recorder.destinationCreatedRecords,
            )
            assertEquals(listOf("repository", "analytics"), recorder.order)
            assertFalse(viewModel.uiState.isSaving)
            assertNull(viewModel.uiState.errorMessage)
            assertEquals(7L, assertNotNull(viewModel.uiState.savedEvent).requestId)
            assertEquals(42L, assertNotNull(viewModel.uiState.savedEvent).destination.id)
        }

    @Test
    fun updatingAnExistingDestinationDoesNotRecordCreation() =
        withViewModel { viewModel, repository, recorder ->
            val existingDestination = NewDestination.copy(id = 9L)
            repository.saveResult = existingDestination.copy(name = "회사")

            viewModel.save(
                destination = existingDestination,
                requestId = 1L,
                loginState = AnalyticsLoginState.LoggedOut,
            )

            assertTrue(recorder.destinationCreatedRecords.isEmpty())
            assertEquals("회사", assertNotNull(viewModel.uiState.savedEvent).destination.name)
        }

    @Test
    fun repositoryResultWithoutAPositiveIdDoesNotRecordCreation() =
        withViewModel { viewModel, repository, recorder ->
            repository.saveResult = NewDestination

            viewModel.save(
                destination = NewDestination,
                requestId = 1L,
                loginState = AnalyticsLoginState.LoggedOut,
            )

            assertTrue(recorder.destinationCreatedRecords.isEmpty())
            assertNotNull(viewModel.uiState.savedEvent)
        }

    @Test
    fun analyticsFailureDoesNotTurnASuccessfulSaveIntoUiFailure() =
        withViewModel { viewModel, repository, recorder ->
            repository.saveResult = NewDestination.copy(id = 5L)
            recorder.destinationCreatedFailure = IllegalStateException("analytics failed")

            viewModel.save(
                destination = NewDestination,
                requestId = 3L,
                loginState = AnalyticsLoginState.LoggedIn,
            )

            assertFalse(viewModel.uiState.isSaving)
            assertNull(viewModel.uiState.errorMessage)
            assertEquals(5L, assertNotNull(viewModel.uiState.savedEvent).destination.id)
        }

    @Test
    fun repositoryFailureDoesNotRecordCreation() =
        withViewModel { viewModel, repository, recorder ->
            repository.saveFailure = IllegalStateException("save failed")

            viewModel.save(
                destination = NewDestination,
                requestId = 4L,
                loginState = AnalyticsLoginState.LoggedIn,
            )

            assertFalse(viewModel.uiState.isSaving)
            assertEquals("save failed", viewModel.uiState.errorMessage)
            assertNull(viewModel.uiState.savedEvent)
            assertTrue(recorder.destinationCreatedRecords.isEmpty())
        }

    @Test
    fun repeatedSaveWhileTheFirstSaveIsRunningIsIgnored() =
        withViewModel { viewModel, repository, recorder ->
            val saveGate = CompletableDeferred<Unit>()
            repository.saveGate = saveGate
            repository.saveResult = NewDestination.copy(id = 8L)

            viewModel.save(
                destination = NewDestination,
                requestId = 11L,
                loginState = AnalyticsLoginState.LoggedIn,
            )
            viewModel.save(
                destination = NewDestination.copy(name = "두 번째 요청"),
                requestId = 12L,
                loginState = AnalyticsLoginState.LoggedIn,
            )

            assertTrue(viewModel.uiState.isSaving)
            assertEquals(1, repository.saveRequests.size)

            saveGate.complete(Unit)

            assertFalse(viewModel.uiState.isSaving)
            assertEquals(11L, assertNotNull(viewModel.uiState.savedEvent).requestId)
            assertEquals(1, recorder.destinationCreatedRecords.size)
        }

    @Test
    fun loggingOutReplacesTheServerDestinationListWithTheLocalDestinationList() =
        withViewModel { viewModel, repository, _ ->
            val localDestination = NewDestination.copy(id = 1L, name = "로컬 집")
            val serverDestination = NewDestination.copy(id = 101L, name = "서버 집")
            repository.localDestinations.value = listOf(localDestination)
            repository.fetchAllResult = listOf(serverDestination)

            viewModel.onScreenEntered()

            assertEquals(listOf(serverDestination), viewModel.uiState.destinations)

            repository.fetchAllResult = listOf(localDestination)
            viewModel.onLoggedOut()

            assertEquals(listOf(localDestination), viewModel.uiState.destinations)
        }

    @Test
    fun loggingOutIgnoresALateServerDestinationResponse() =
        withViewModel { viewModel, repository, _ ->
            val localDestination = NewDestination.copy(id = 1L, name = "로컬 집")
            val serverDestination = NewDestination.copy(id = 101L, name = "서버 집")
            val serverFetchGate = CompletableDeferred<Unit>()
            repository.localDestinations.value = listOf(localDestination)
            repository.fetchAllResult = listOf(serverDestination)
            repository.nextFetchGate = serverFetchGate
            repository.ignoreNextFetchCancellation = true

            viewModel.onScreenEntered()

            repository.fetchAllResult = listOf(localDestination)
            viewModel.onLoggedOut()

            assertEquals(listOf(localDestination), viewModel.uiState.destinations)

            serverFetchGate.complete(Unit)

            assertEquals(listOf(localDestination), viewModel.uiState.destinations)
        }
}

private inline fun withViewModel(
    block: (
        DestinationViewModel,
        FakeDestinationRepository,
        RecordingProductAnalyticsRecorder,
    ) -> Unit,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val repository = FakeDestinationRepository()
    val recorder = RecordingProductAnalyticsRecorder()
    val viewModel = DestinationViewModel(
        repository = repository,
        productAnalyticsRecorder = recorder,
        coroutineScope = scope,
    )
    try {
        block(viewModel, repository, recorder)
    } finally {
        scope.cancel()
    }
}

private class FakeDestinationRepository : DestinationRepository {
    val localDestinations = MutableStateFlow(emptyList<SavedDestination>())

    val saveRequests = mutableListOf<SavedDestination>()
    var fetchAllResult: List<SavedDestination> = emptyList()
    var nextFetchGate: CompletableDeferred<Unit>? = null
    var ignoreNextFetchCancellation = false
    var saveResult: SavedDestination = NewDestination.copy(id = 1L)
    var saveGate: CompletableDeferred<Unit>? = null
    var saveFailure: Throwable? = null
    var order: MutableList<String>? = null

    override fun observeAll(): Flow<List<SavedDestination>> = localDestinations

    override suspend fun fetchAll(): List<SavedDestination> {
        val result = fetchAllResult
        val gate = nextFetchGate
        val ignoreCancellation = ignoreNextFetchCancellation
        nextFetchGate = null
        ignoreNextFetchCancellation = false
        if (gate != null) {
            if (ignoreCancellation) {
                withContext(NonCancellable) {
                    gate.await()
                }
            } else {
                gate.await()
            }
        }
        return result
    }

    override suspend fun sync(): List<SavedDestination> = localDestinations.value

    override suspend fun save(destination: SavedDestination): SavedDestination {
        saveRequests += destination
        saveGate?.await()
        saveFailure?.let { throw it }
        order?.add("repository")
        return saveResult
    }

    override suspend fun updateName(id: Long, name: String): Boolean = false

    override suspend fun delete(id: Long): Boolean = false
}

private data class DestinationCreatedRecord(
    val destinationId: Long,
    val loginState: AnalyticsLoginState,
)

private class RecordingProductAnalyticsRecorder : ProductAnalyticsRecorder {
    val destinationCreatedRecords = mutableListOf<DestinationCreatedRecord>()
    val order = mutableListOf<String>()
    var destinationCreatedFailure: Throwable? = null

    override fun recordDestinationCreated(
        destinationId: Long,
        loginState: AnalyticsLoginState,
    ) {
        order += "analytics"
        destinationCreatedFailure?.let { throw it }
        destinationCreatedRecords += DestinationCreatedRecord(destinationId, loginState)
    }

    override fun recordDestinationSelected(
        source: DestinationSelectionSource,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordStampCalendarViewed(
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordStampMonthChanged(
        direction: StampMonthChangeDirection,
        year: Int,
        month: Int,
        loginState: AnalyticsLoginState,
    ) = Unit

    override fun recordAccountWithdrawalCompleted() = Unit

    override fun recordLoginStarted(provider: AnalyticsAuthProvider) = Unit

    override fun recordLoginCompleted(
        provider: AnalyticsAuthProvider,
        isNewUser: Boolean,
    ) = Unit

    override fun recordSignupCompleted(provider: AnalyticsAuthProvider) = Unit
}

private val NewDestination = SavedDestination(
    name = "집",
    address = "서울특별시 중구 세종대로 110",
    latitude = 37.5665851,
    longitude = 126.9782038,
)
