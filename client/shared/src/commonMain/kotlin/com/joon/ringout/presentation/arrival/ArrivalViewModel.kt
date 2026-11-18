package com.joon.ringout.presentation.arrival

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class ArrivalViewModel(
    private val occurrenceId: String,
    private val repository: ArrivalResultRepository,
    coroutineScope: CoroutineScope? = null,
    private val currentTimeMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = mutableUiState.asStateFlow()

    private val scope = coroutineScope ?: viewModelScope
    private var loadJob: Job? = null
    private var confirmationJob: Job? = null
    private var confirmationGeneration = 0L

    init {
        load()
    }

    fun retry() {
        if (loadJob?.isActive == true || uiState.value.isAcknowledged) return
        load()
    }

    /** Acknowledge the receipt before leaving, including when back is pressed during loading. */
    fun confirm() {
        if (confirmationJob?.isActive == true || uiState.value.isAcknowledged) return
        val generation = ++confirmationGeneration
        mutableUiState.update { it.copy(isConfirming = true, confirmationErrorMessage = null) }
        confirmationJob = scope.launch {
            try {
                repository.acknowledgeThrough(occurrenceId, currentTimeMillis())
                currentCoroutineContext().ensureActive()
                mutableUiState.update { it.copy(isConfirming = false, isAcknowledged = true) }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableUiState.update {
                    it.copy(
                        isConfirming = false,
                        confirmationErrorMessage = "도착 확인을 저장하지 못했어요. 다시 시도해 주세요.",
                    )
                }
            } finally {
                if (generation == confirmationGeneration) {
                    mutableUiState.update { it.copy(isConfirming = false) }
                }
            }
        }
    }

    private fun load() {
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = scope.launch {
            try {
                val result = repository.getResult(occurrenceId)
                currentCoroutineContext().ensureActive()
                mutableUiState.update {
                    it.copy(
                        result = result,
                        isLoading = false,
                        errorMessage = if (result == null) "도착 기록을 찾을 수 없어요." else null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "도착 기록을 불러오지 못했어요. 다시 시도해 주세요.",
                    )
                }
            }
        }
    }
}
