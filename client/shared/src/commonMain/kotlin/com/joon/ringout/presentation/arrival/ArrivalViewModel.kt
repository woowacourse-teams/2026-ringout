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
import kotlinx.coroutines.launch

class ArrivalViewModel(
    private val occurrenceId: String,
    private val repository: ArrivalResultRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ArrivalUiState())
    val uiState: StateFlow<ArrivalUiState> = mutableUiState.asStateFlow()

    private val scope = coroutineScope ?: viewModelScope
    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() {
        if (loadJob?.isActive == true) return
        load()
    }

    private fun load() {
        mutableUiState.value = ArrivalUiState()
        loadJob = scope.launch {
            try {
                val result = repository.getResult(occurrenceId)
                currentCoroutineContext().ensureActive()
                mutableUiState.value = ArrivalUiState(
                    result = result,
                    isLoading = false,
                    errorMessage = if (result == null) "도착 기록을 찾을 수 없어요." else null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                mutableUiState.value = ArrivalUiState(
                    isLoading = false,
                    errorMessage = "도착 기록을 불러오지 못했어요. 다시 시도해 주세요.",
                )
            }
        }
    }
}
