package com.joon.ringout.presentation.arrival

import com.joon.ringout.domain.arrival.ArrivalGrowth
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArrivalViewModelTest {
    @Test
    fun `저장된 도착 회차만 읽어 도착 당시 성장 상태를 표시한다`() = runTest {
        val pendingResult = CompletableDeferred<ArrivalResult?>()
        val repository = ArrivalReadRepository { pendingResult.await() }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)

        assertTrue(viewModel.uiState.value.isLoading)
        runCurrent()
        assertEquals(listOf("arrival-4"), repository.requestedIds)
        assertNull(viewModel.uiState.value.result)

        pendingResult.complete(SavedArrival)
        runCurrent()

        assertEquals(SavedArrival, viewModel.uiState.value.result)
        assertEquals(4, viewModel.uiState.value.result?.growth?.filledSlots)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `조회 오류 후 재시도하면 오류를 지우고 저장된 결과를 불러온다`() = runTest {
        val repository = ArrivalReadRepository { error("저장소 조회 실패") }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.result)
        assertEquals("도착 기록을 불러오지 못했어요. 다시 시도해 주세요.", viewModel.uiState.value.errorMessage)

        repository.readResult = { SavedArrival }
        viewModel.retry()
        assertTrue(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        runCurrent()

        assertEquals(SavedArrival, viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(listOf("arrival-4", "arrival-4"), repository.requestedIds)
    }

    @Test
    fun `저장되지 않은 도착 회차는 기록을 찾을 수 없다는 안내를 표시한다`() = runTest {
        val repository = ArrivalReadRepository { null }
        val viewModel = ArrivalViewModel("missing-arrival", repository, backgroundScope)
        runCurrent()

        assertEquals(listOf("missing-arrival"), repository.requestedIds)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.result)
        assertEquals("도착 기록을 찾을 수 없어요.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `조회 중 재시도를 여러 번 눌러도 같은 요청을 중복 실행하지 않는다`() = runTest {
        val pendingResult = CompletableDeferred<ArrivalResult?>()
        val repository = ArrivalReadRepository { pendingResult.await() }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()

        viewModel.retry()
        viewModel.retry()
        runCurrent()
        assertEquals(listOf("arrival-4"), repository.requestedIds)

        pendingResult.complete(SavedArrival)
        runCurrent()
        assertEquals(SavedArrival, viewModel.uiState.value.result)
    }

    @Test
    fun `조회 취소는 실패 안내로 변환하지 않고 코루틴에 전파한다`() = runTest {
        val repository = ArrivalReadRepository { throw CancellationException("조회 취소") }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()

        assertTrue(repository.readJobs.single().isCancelled)
        assertNull(viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.result)

        repository.readResult = { SavedArrival }
        viewModel.retry()
        runCurrent()
        assertEquals(SavedArrival, viewModel.uiState.value.result)
    }

    @Test
    fun `취소된 조회가 늦게 반환되어도 재시도 결과를 덮어쓰지 않는다`() = runTest {
        val delayedResult = CompletableDeferred<ArrivalResult?>()
        val repository = ArrivalReadRepository {
            withContext(NonCancellable) { delayedResult.await() }
        }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()

        repository.readJobs.single().cancel()
        val enrichedResult = SavedArrival.copy(alarmDismissedAtEpochMillis = 500)
        repository.readResult = { enrichedResult }
        viewModel.retry()
        runCurrent()
        assertEquals(enrichedResult, viewModel.uiState.value.result)

        delayedResult.complete(SavedArrival)
        runCurrent()
        assertEquals(enrichedResult, viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `도착 확인 저장이 끝나야 화면 이동을 허용하고 중복 확인을 무시한다`() = runTest {
        val pendingConfirmation = CompletableDeferred<Unit>()
        val repository = ArrivalReadRepository { SavedArrival }.apply {
            acknowledge = { _, _ -> pendingConfirmation.await() }
        }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope) { 2_000 }
        runCurrent()

        viewModel.confirm()
        viewModel.confirm()
        assertTrue(viewModel.uiState.value.isConfirming)
        assertFalse(viewModel.uiState.value.isAcknowledged)
        runCurrent()
        assertEquals(listOf("arrival-4" to 2_000L), repository.confirmedRequests)

        pendingConfirmation.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isConfirming)
        assertTrue(viewModel.uiState.value.isAcknowledged)
        viewModel.confirm()
        viewModel.retry()
        runCurrent()
        assertEquals(1, repository.confirmedRequests.size)
        assertEquals(1, repository.requestedIds.size)
    }

    @Test
    fun `도착 확인 저장 실패는 결과를 유지하고 다시 확인하면 이동할 수 있다`() = runTest {
        val repository = ArrivalReadRepository { SavedArrival }.apply {
            acknowledge = { _, _ -> error("확인 저장 실패") }
        }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()
        viewModel.confirm()
        runCurrent()

        assertEquals(SavedArrival, viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isAcknowledged)
        assertFalse(viewModel.uiState.value.isConfirming)
        assertNotNull(viewModel.uiState.value.confirmationErrorMessage)

        repository.acknowledge = { _, _ -> }
        viewModel.confirm()
        assertNull(viewModel.uiState.value.confirmationErrorMessage)
        runCurrent()
        assertTrue(viewModel.uiState.value.isAcknowledged)
        assertEquals(2, repository.confirmedRequests.size)
    }

    @Test
    fun `조회 중 뒤로 가기로 확인한 뒤 늦게 도착한 조회는 확인 완료 상태를 보존한다`() = runTest {
        val pendingResult = CompletableDeferred<ArrivalResult?>()
        val repository = ArrivalReadRepository { pendingResult.await() }.apply { acknowledge = { _, _ -> } }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()
        viewModel.confirm()
        runCurrent()
        assertTrue(viewModel.uiState.value.isAcknowledged)
        assertTrue(viewModel.uiState.value.isLoading)

        pendingResult.complete(SavedArrival)
        runCurrent()
        assertTrue(viewModel.uiState.value.isAcknowledged)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `조회가 실패해도 뒤로 가기 확인 저장을 완료할 수 있다`() = runTest {
        val repository = ArrivalReadRepository { error("조회 실패") }.apply { acknowledge = { _, _ -> } }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()
        assertNotNull(viewModel.uiState.value.errorMessage)
        viewModel.confirm()
        runCurrent()
        assertTrue(viewModel.uiState.value.isAcknowledged)
    }

    @Test
    fun `조회 재시도 응답은 동시에 진행 중인 확인 실패 안내를 지우지 않는다`() = runTest {
        val pendingResult = CompletableDeferred<ArrivalResult?>()
        val repository = ArrivalReadRepository { error("조회 실패") }.apply {
            acknowledge = { _, _ -> error("확인 저장 실패") }
        }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()
        repository.readResult = { pendingResult.await() }
        viewModel.retry()
        viewModel.confirm()
        runCurrent()
        val error = viewModel.uiState.value.confirmationErrorMessage
        assertNotNull(error)
        pendingResult.complete(SavedArrival)
        runCurrent()
        assertEquals(error, viewModel.uiState.value.confirmationErrorMessage)
        assertEquals(SavedArrival, viewModel.uiState.value.result)
    }

    @Test
    fun `취소된 확인이 늦게 완료되어도 새 확인 요청의 저장 중 상태를 지우지 않는다`() = runTest {
        val cancelledConfirmation = CompletableDeferred<Unit>()
        val currentConfirmation = CompletableDeferred<Unit>()
        val repository = ArrivalReadRepository { SavedArrival }.apply {
            acknowledge = { _, _ -> withContext(NonCancellable) { cancelledConfirmation.await() } }
        }
        val viewModel = ArrivalViewModel("arrival-4", repository, backgroundScope)
        runCurrent()
        viewModel.confirm()
        runCurrent()
        repository.confirmationJobs.single().cancel()
        repository.acknowledge = { _, _ -> currentConfirmation.await() }
        viewModel.confirm()
        runCurrent()

        cancelledConfirmation.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value.isConfirming)
        assertFalse(viewModel.uiState.value.isAcknowledged)
        assertNull(viewModel.uiState.value.confirmationErrorMessage)

        currentConfirmation.complete(Unit)
        runCurrent()
        assertTrue(viewModel.uiState.value.isAcknowledged)
        assertFalse(viewModel.uiState.value.isConfirming)
    }
}

private val SavedArrival = ArrivalResult(
    occurrenceId = "arrival-4",
    completedLocalDate = MissionDate.parse("2026-11-09"),
    completedAtEpochMillis = 1_000,
    alarmDismissedAtEpochMillis = null,
    successfulDayCountAtArrival = 4,
    grewOnThisArrival = true,
    acknowledgedAtEpochMillis = null,
)

private class ArrivalReadRepository(
    var readResult: suspend () -> ArrivalResult?,
) : ArrivalResultRepository {
    val requestedIds = mutableListOf<String>()
    val readJobs = mutableListOf<Job>()
    var acknowledge: suspend (String, Long) -> Unit = { _, _ ->
        error("결과 조회 중 확인 상태를 변경하면 안 됩니다.")
    }
    val confirmedRequests = mutableListOf<Pair<String, Long>>()
    val confirmationJobs = mutableListOf<Job>()

    override suspend fun getResult(occurrenceId: String): ArrivalResult? {
        requestedIds += occurrenceId
        readJobs += assertNotNull(currentCoroutineContext()[Job])
        return readResult()
    }

    override fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?> =
        error("지정된 회차만 조회해야 합니다.")

    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long) {
        confirmedRequests += occurrenceId to acknowledgedAtEpochMillis
        confirmationJobs += assertNotNull(currentCoroutineContext()[Job])
        acknowledge(occurrenceId, acknowledgedAtEpochMillis)
    }

    override suspend fun getGrowth(): ArrivalGrowth = error("저장된 결과의 성장 상태를 사용해야 합니다.")

    override fun observeGrowth(): Flow<ArrivalGrowth> = error("저장된 결과의 성장 상태를 사용해야 합니다.")
}
