package com.joon.ringout.presentation.destination

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DestinationUiState(
    val destinations: List<SavedDestination> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val savedEvent: DestinationSavedEvent? = null,
)

data class DestinationSavedEvent(
    val eventId: Long,
    val requestId: Long,
    val destination: SavedDestination,
)

internal fun DestinationSavedEvent.belongsToDestinationRequest(
    currentRequestId: Long,
    isDestinationScreenVisible: Boolean,
): Boolean = isDestinationScreenVisible && requestId == currentRequestId

class DestinationViewModel(
    private val repository: DestinationRepository,
) : ViewModel() {
    var uiState by mutableStateOf(DestinationUiState())
        private set

    private val mutationMutex = Mutex()
    private var nextSavedEventId = 0L
    private var fetchJob: Job? = null

    init {
        observeDestinations()
    }

    fun onScreenEntered() {
        fetchJob?.cancel()
        uiState = uiState.copy(isLoading = true, errorMessage = null)
        fetchJob = viewModelScope.launch {
            try {
                val destinations = repository.fetchAll()
                uiState = uiState.copy(
                    destinations = destinations,
                    isLoading = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isLoading = false,
                    errorMessage = error.message ?: "목적지를 불러오지 못했어요.",
                )
            }
        }
    }

    fun save(
        destination: SavedDestination,
        requestId: Long,
    ) {
        if (uiState.isSaving) return

        uiState = uiState.copy(
            isSaving = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                val savedDestination = mutationMutex.withLock {
                    repository.save(destination)
                }
                uiState = uiState.copy(
                    isSaving = false,
                    savedEvent = DestinationSavedEvent(
                        eventId = ++nextSavedEventId,
                        requestId = requestId,
                        destination = savedDestination,
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "목적지를 저장하지 못했어요.",
                )
            }
        }
    }

    fun rename(
        destinationId: Long,
        nickname: String,
    ) {
        if (uiState.isSaving) return

        uiState = uiState.copy(
            isSaving = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                val renamed = mutationMutex.withLock {
                    repository.updateName(destinationId, nickname)
                }
                uiState = if (renamed) {
                    uiState.copy(isSaving = false)
                } else {
                    uiState.copy(
                        isSaving = false,
                        errorMessage = "수정할 목적지를 찾지 못했어요.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "목적지 별명을 수정하지 못했어요.",
                )
            }
        }
    }

    fun delete(destinationId: Long) {
        if (uiState.isSaving) return

        uiState = uiState.copy(
            isSaving = true,
            errorMessage = null,
        )
        viewModelScope.launch {
            try {
                val deleted = mutationMutex.withLock {
                    repository.delete(destinationId)
                }
                uiState = if (deleted) {
                    uiState.copy(
                        destinations = uiState.destinations.filterNot { it.id == destinationId },
                        isSaving = false,
                    )
                } else {
                    uiState.copy(
                        isSaving = false,
                        errorMessage = "삭제할 목적지를 찾지 못했어요.",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                uiState = uiState.copy(
                    isSaving = false,
                    errorMessage = error.message ?: "목적지를 삭제하지 못했어요.",
                )
            }
        }
    }

    fun consumeSavedEvent(eventId: Long) {
        if (uiState.savedEvent?.eventId == eventId) {
            uiState = uiState.copy(savedEvent = null)
        }
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    private fun observeDestinations() {
        viewModelScope.launch {
            repository.observeAll()
                .retryWhen { error, attempt ->
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "저장된 목적지를 불러오지 못했어요.",
                    )
                    delay(minOf(5_000L, 250L * (attempt + 1L)))
                    true
                }
                .collect { destinations ->
                    uiState = uiState.copy(
                        destinations = destinations,
                        isLoading = false,
                    )
                }
        }
    }
}
