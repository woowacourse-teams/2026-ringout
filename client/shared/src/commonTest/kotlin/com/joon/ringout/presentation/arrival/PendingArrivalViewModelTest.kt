package com.joon.ringout.presentation.arrival

import com.joon.ringout.domain.arrival.ArrivalGrowth
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.MissionDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PendingArrivalViewModelTest {
    @Test
    fun `첫 조회 전 상태와 미확인 결과가 없다는 조회 결과를 구분한다`() = runTest {
        val results = MutableSharedFlow<ArrivalResult?>()
        val model = PendingArrivalViewModel(PendingArrivalRepository { results }, backgroundScope)
        runCurrent()
        assertFalse(model.uiState.value.isLoaded)

        results.emit(null)
        runCurrent()
        assertTrue(model.uiState.value.isLoaded)
        assertNull(model.uiState.value.result)

        results.emit(PendingReceipt)
        runCurrent()
        assertEquals(PendingReceipt, model.uiState.value.result)
    }

    @Test
    fun `새 관찰자를 만들어도 저장소의 미확인 결과를 다시 표시하고 확인 이후에는 비운다`() = runTest {
        val results = MutableStateFlow<ArrivalResult?>(PendingReceipt)
        val repository = PendingArrivalRepository { results }
        val original = PendingArrivalViewModel(repository, backgroundScope)
        runCurrent()
        val recreated = PendingArrivalViewModel(repository, backgroundScope)
        runCurrent()
        assertEquals(PendingReceipt, original.uiState.value.result)
        assertEquals(PendingReceipt, recreated.uiState.value.result)

        results.value = null
        runCurrent()
        assertTrue(recreated.uiState.value.isLoaded)
        assertNull(original.uiState.value.result)
        assertNull(recreated.uiState.value.result)
    }

    @Test
    fun `첫 조회 실패는 결과 없음으로 처리하지 않고 다시 조회한다`() = runTest {
        val results = MutableStateFlow<ArrivalResult?>(PendingReceipt)
        var subscriptions = 0
        val repository = PendingArrivalRepository {
            flow {
                if (++subscriptions == 1) error("일시적인 저장소 오류")
                emitAll(results)
            }
        }
        val model = PendingArrivalViewModel(repository, backgroundScope)
        runCurrent()
        assertFalse(model.uiState.value.isLoaded)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(2, subscriptions)
        assertEquals(PendingReceipt, model.uiState.value.result)
        assertTrue(model.uiState.value.isLoaded)
    }

    @Test
    fun `관찰 중 오류가 생겨도 기존 미확인 결과는 유지한다`() = runTest {
        var subscriptions = 0
        val model = PendingArrivalViewModel(
            PendingArrivalRepository {
                flow {
                    if (++subscriptions == 1) {
                        emit(PendingReceipt)
                        error("관찰 연결 끊김")
                    }
                    emit(null)
                }
            },
            backgroundScope,
        )
        runCurrent()
        assertEquals(PendingReceipt, model.uiState.value.result)
        assertTrue(model.uiState.value.isLoaded)

        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(model.uiState.value.isLoaded)
        assertNull(model.uiState.value.result)
    }

    @Test
    fun `취소된 관찰은 재시도하지 않는다`() = runTest {
        var subscriptions = 0
        val model = PendingArrivalViewModel(
            PendingArrivalRepository {
                flow {
                    subscriptions++
                    throw CancellationException("관찰 종료")
                }
            },
            backgroundScope,
        )
        runCurrent()
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1, subscriptions)
        assertFalse(model.uiState.value.isLoaded)
    }
}

private val PendingReceipt = ArrivalResult(
    occurrenceId = "pending-arrival",
    completedLocalDate = MissionDate.parse("2026-11-09"),
    completedAtEpochMillis = 1_000,
    alarmDismissedAtEpochMillis = 500,
    successfulDayCountAtArrival = 4,
    grewOnThisArrival = true,
    acknowledgedAtEpochMillis = null,
)

private class PendingArrivalRepository(
    private val observe: () -> Flow<ArrivalResult?>,
) : ArrivalResultRepository {
    override fun observeLatestUnacknowledgedResult() = observe()
    override suspend fun getResult(occurrenceId: String): ArrivalResult? = error("관찰만 해야 합니다.")
    override suspend fun acknowledgeThrough(occurrenceId: String, acknowledgedAtEpochMillis: Long): Unit =
        error("관찰 중 확인 상태를 변경하면 안 됩니다.")
    override suspend fun getGrowth(): ArrivalGrowth = error("저장된 결과를 사용해야 합니다.")
    override fun observeGrowth(): Flow<ArrivalGrowth> = error("저장된 결과를 사용해야 합니다.")
}
