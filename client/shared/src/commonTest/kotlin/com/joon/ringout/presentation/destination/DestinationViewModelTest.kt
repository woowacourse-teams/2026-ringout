package com.joon.ringout.presentation.destination

import com.joon.ringout.analytics.AnalyticsAuthProvider
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.DestinationSelectionSource
import com.joon.ringout.analytics.NoOpOnboardingAnalyticsRecorder
import com.joon.ringout.analytics.OnboardingAnalyticsRecorder
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.StampMonthChangeDirection
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.app.AppLocationCacheViewModel
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
    fun `관리 저장은 기존 아이디의 별명 주소 좌표를 수정하고 목적지 선택 이벤트를 만들지 않는다`() =
        withViewModel { viewModel, repository, recorder ->
            val original = NewDestination.copy(id = 9)
            repository.localDestinations.value = listOf(original)
            assertTrue(viewModel.beginManagement(9))
            val edited = original.copy(name = " 도서관 ", address = "경기도 수원시", latitude = 37.28, longitude = 127.01)
            repository.saveResult = edited.copy(name = "도서관")

            viewModel.saveManagedDestination(edited)

            assertEquals(listOf(edited.copy(name = "도서관")), repository.saveRequests)
            assertEquals(listOf(repository.saveResult), viewModel.uiState.destinations)
            assertNull(viewModel.uiState.managedDestination)
            assertNull(viewModel.uiState.savedEvent)
            assertTrue(recorder.destinationCreatedRecords.isEmpty())
        }

    @Test
    fun `관리 화면을 취소하면 원본 목적지를 변경하지 않는다`() =
        withViewModel { viewModel, repository, _ ->
            val original = NewDestination.copy(id = 9)
            repository.localDestinations.value = listOf(original)
            viewModel.beginManagement(9)
            viewModel.closeManagement()
            assertEquals(listOf(original), viewModel.uiState.destinations)
            assertTrue(repository.saveRequests.isEmpty())
            assertTrue(repository.deleteRequests.isEmpty())
            assertNull(viewModel.uiState.managedDestination)
        }

    @Test
    fun `관리 저장 실패 시 화면과 원본을 유지하고 재시도할 수 있다`() =
        withViewModel { viewModel, repository, _ ->
            val original = NewDestination.copy(id = 9)
            repository.localDestinations.value = listOf(original)
            repository.saveFailure = IllegalStateException("저장 실패")
            viewModel.beginManagement(9)
            viewModel.saveManagedDestination(original.copy(name = "회사"))
            assertEquals(original, viewModel.uiState.managedDestination)
            assertEquals(listOf(original), viewModel.uiState.destinations)
            assertEquals("저장 실패", viewModel.uiState.errorMessage)
            assertFalse(viewModel.uiState.isSaving)
            repository.saveFailure = null
            repository.saveResult = original.copy(name = "회사")
            viewModel.saveManagedDestination(repository.saveResult)
            assertNull(viewModel.uiState.managedDestination)
            assertNull(viewModel.uiState.errorMessage)
        }

    @Test
    fun `관리 작업 중 중복 저장 삭제와 화면 이탈을 막는다`() =
        withViewModel { viewModel, repository, _ ->
            val original = NewDestination.copy(id = 9)
            repository.localDestinations.value = listOf(original)
            val gate = CompletableDeferred<Unit>()
            repository.saveGate = gate
            repository.saveResult = original
            viewModel.beginManagement(9)
            viewModel.saveManagedDestination(original)
            viewModel.saveManagedDestination(original)
            viewModel.deleteManagedDestination()
            viewModel.closeManagement()
            assertFalse(viewModel.beginManagement(9))
            assertEquals(original, viewModel.uiState.managedDestination)
            assertEquals(1, repository.saveRequests.size)
            assertTrue(repository.deleteRequests.isEmpty())
            gate.complete(Unit)
            assertNull(viewModel.uiState.managedDestination)
        }

    @Test
    fun `관리 삭제 성공 시 해당 항목만 제거하고 선택 이벤트를 만들지 않는다`() =
        withViewModel { viewModel, repository, _ ->
            val original = NewDestination.copy(id = 9)
            val other = original.copy(id = 10)
            repository.localDestinations.value = listOf(original, other)
            repository.deleteResult = true
            viewModel.beginManagement(9)
            viewModel.deleteManagedDestination()
            assertEquals(listOf(9L), repository.deleteRequests)
            assertEquals(listOf(other), viewModel.uiState.destinations)
            assertNull(viewModel.uiState.managedDestination)
            assertNull(viewModel.uiState.savedEvent)
        }

    @Test
    fun `삭제 실패와 잘못된 저장 입력은 원본을 유지한다`() =
        withViewModel { viewModel, repository, _ ->
            val original = NewDestination.copy(id = 9)
            repository.localDestinations.value = listOf(original)
            assertFalse(viewModel.beginManagement(99))
            viewModel.beginManagement(9)
            viewModel.saveManagedDestination(original.copy(id = 10))
            viewModel.saveManagedDestination(original.copy(name = "가".repeat(13)))
            assertTrue(repository.saveRequests.isEmpty())
            viewModel.deleteManagedDestination()
            assertEquals(original, viewModel.uiState.managedDestination)
            assertEquals(listOf(original), viewModel.uiState.destinations)
            assertNotNull(viewModel.uiState.errorMessage)
            assertFalse(viewModel.uiState.isSaving)
        }

    @Test
    fun `홈까지 나간 뒤 새 목적지 화면 모델로 진입해도 앱의 현재 위치 캐시를 재사용한다`() {
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val appViewModel = AppLocationCacheViewModel(
            authSession = AuthSession().apply { clear() },
            coroutineScope = appScope,
        )
        try {
            withViewModel(appViewModel.destinationCurrentLocationCache) { viewModel, _, _ ->
                viewModel.onLoggedOut()
                viewModel.onScreenEntered()
                assertNull(viewModel.initialCurrentLocation())
                viewModel.onCurrentLocationResolved(latitude = 37.5665, longitude = 126.9780)
            }

            withViewModel(appViewModel.destinationCurrentLocationCache) { viewModel, _, _ ->
                viewModel.onLoggedOut()
                viewModel.onScreenEntered()

                val initialLocation = assertNotNull(viewModel.initialCurrentLocation())
                assertEquals(37.5665, initialLocation.latitude)
                assertEquals(126.9780, initialLocation.longitude)
            }
        } finally {
            appScope.cancel()
        }
    }

    @Test
    fun `목적지 화면에 다시 진입해도 마지막 현재 위치를 초기 위치로 유지한다`() =
        withViewModel { viewModel, _, _ ->
            viewModel.onCurrentLocationResolved(latitude = 37.5665, longitude = 126.9780)

            viewModel.onScreenEntered()
            viewModel.onScreenEntered()

            val initialLocation = assertNotNull(viewModel.initialCurrentLocation())
            assertEquals(37.5665, initialLocation.latitude)
            assertEquals(126.9780, initialLocation.longitude)
        }

    @Test
    fun `사용자가 다른 목적지를 저장해도 현재 위치 캐시는 변경되지 않는다`() =
        withViewModel { viewModel, _, _ ->
            viewModel.onCurrentLocationResolved(latitude = 35.1796, longitude = 129.0756)

            viewModel.save(
                destination = NewDestination,
                requestId = 1L,
                loginState = AnalyticsLoginState.LoggedIn,
            )

            val initialLocation = assertNotNull(viewModel.initialCurrentLocation())
            assertEquals(35.1796, initialLocation.latitude)
            assertEquals(129.0756, initialLocation.longitude)
        }

    @Test
    fun `비로그인 화면 상태를 다시 반영해도 마지막 현재 위치를 유지한다`() =
        withViewModel { viewModel, _, _ ->
            viewModel.onCurrentLocationResolved(latitude = 37.5665, longitude = 126.9780)

            viewModel.onLoggedOut()

            val initialLocation = assertNotNull(viewModel.initialCurrentLocation())
            assertEquals(37.5665, initialLocation.latitude)
            assertEquals(126.9780, initialLocation.longitude)
        }

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
    currentLocationCache: DestinationCurrentLocationCache = DestinationCurrentLocationCache(),
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
        currentLocationCache = currentLocationCache,
    )
    try {
        block(viewModel, repository, recorder)
    } finally {
        scope.cancel()
    }
}

private class FakeDestinationRepository : DestinationRepository {
    val localDestinations = MutableStateFlow(emptyList<SavedDestination>())

    val deleteRequests = mutableListOf<Long>()
    var deleteResult = false
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

    override suspend fun delete(id: Long): Boolean {
        deleteRequests += id
        return deleteResult
    }
}

private data class DestinationCreatedRecord(
    val destinationId: Long,
    val loginState: AnalyticsLoginState,
)

private class RecordingProductAnalyticsRecorder : ProductAnalyticsRecorder,
    OnboardingAnalyticsRecorder by NoOpOnboardingAnalyticsRecorder {
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
