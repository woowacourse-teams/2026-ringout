package com.joon.ringout.presentation.arrival

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.arrival.ArrivalResult
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch

data class PendingArrivalUiState(
    val isLoaded: Boolean = false,
    val result: ArrivalResult? = null,
)

/** Persistent receipts remain authoritative across foreground events, gates and process recreation. */
class PendingArrivalViewModel(
    repository: ArrivalResultRepository,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PendingArrivalUiState())
    val uiState: StateFlow<PendingArrivalUiState> = mutableUiState.asStateFlow()

    init {
        (coroutineScope ?: viewModelScope).launch {
            flow { emitAll(repository.observeLatestUnacknowledgedResult()) }
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) throw cause
                    if (cause !is Exception) return@retryWhen false
                    // Preserve the last receipt during a transient read failure; do not consume it.
                    delay(1_000)
                    true
                }
                .collect { result ->
                    mutableUiState.value = PendingArrivalUiState(isLoaded = true, result = result)
                }
        }
    }
}
