package com.joon.ringout.presentation.destination

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.analytics.AnalyticsLoginState
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.domain.destination.DestinationRepository
import com.joon.ringout.domain.destination.SavedDestination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    private val productAnalyticsRecorder: ProductAnalyticsRecorder,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    var uiState by mutableStateOf(DestinationUiState())
        private set

    private val mutationMutex = Mutex()
    private val scope = coroutineScope ?: viewModelScope
    private var nextSavedEventId = 0L
    private var fetchJob: Job? = null
    private var fetchRequestId = 0L

    init {
        observeDestinations()
    }

    fun onScreenEntered() {
        refreshDestinations()
    }

    fun onLoggedOut() {
        refreshDestinations(clearDestinations = true)
    }

    private fun refreshDestinations(clearDestinations: Boolean = false) {
        fetchJob?.cancel()
        val currentRequestId = ++fetchRequestId
        uiState = uiState.copy(
            destinations = if (clearDestinations) emptyList() else uiState.destinations,
            isLoading = true,
            errorMessage = null,
        )
        fetchJob = scope.launch {
            try {
                val destinations = repository.fetchAll()
                if (currentRequestId != fetchRequestId) return@launch
                uiState = uiState.copy(
                    destinations = destinations,
                    isLoading = false,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (currentRequestId == fetchRequestId) {
                    uiState = uiState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "목적지를 불러오지 못했어요.",
                    )
                }
            }
        }
    }

    fun save(
        destination: SavedDestination,
        requestId: Long,
        loginState: AnalyticsLoginState,
    ) {
        if (uiState.isSaving) return

        uiState = uiState.copy(
            isSaving = true,
            errorMessage = null,
        )
        scope.launch {
            try {
                val savedDestination = mutationMutex.withLock {
                    repository.save(destination)
                }
                if (destination.id == 0L && savedDestination.id > 0L) {
                    runCatching {
                        productAnalyticsRecorder.recordDestinationCreated(
                            destinationId = savedDestination.id,
                            loginState = loginState,
                        )
                    }
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
        scope.launch {
            try {
                val renamed = mutationMutex.withLock {
                    repository.updateName(destinationId, nickname)
                }
                uiState = if (renamed) {
                    uiState.copy(
                        destinations = uiState.destinations.map { destination ->
                            if (destination.id == destinationId) {
                                destination.copy(name = nickname)
                            } else {
                                destination
                            }
                        },
                        isSaving = false,
                    )
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
        scope.launch {
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
        scope.launch {
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
