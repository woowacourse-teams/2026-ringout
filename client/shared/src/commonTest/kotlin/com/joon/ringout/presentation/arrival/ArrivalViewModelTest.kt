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

    override suspend fun getResult(occurrenceId: String): ArrivalResult? {
        requestedIds += occurrenceId
        readJobs += assertNotNull(currentCoroutineContext()[Job])
        return readResult()
    }

    override fun observeLatestUnacknowledgedResult(): Flow<ArrivalResult?> =
        error("지정된 회차만 조회해야 합니다.")

    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long): Unit =
        error("결과 조회 중 확인 상태를 변경하면 안 됩니다.")

    override suspend fun getGrowth(): ArrivalGrowth = error("저장된 결과의 성장 상태를 사용해야 합니다.")

    override fun observeGrowth(): Flow<ArrivalGrowth> = error("저장된 결과의 성장 상태를 사용해야 합니다.")
}
