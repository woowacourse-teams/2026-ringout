package com.joon.ringout.presentation.activemission

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.alarm.ForceEndMissionResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class ForceEndPhase { Idle, Processing, Completed, Failed }

internal data class ForceEndMissionUiState(
    val occurrenceId: String? = null,
    val phase: ForceEndPhase = ForceEndPhase.Idle,
)

/** App-scoped: mission screen removal or Activity recreation must not cancel a stop. */
internal class ForceEndMissionViewModel(
    private val savedState: SavedStateHandle,
    private val coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        ForceEndMissionUiState(
            occurrenceId = savedState[OccurrenceKey],
            phase = savedState.get<String>(PhaseKey)?.let(ForceEndPhase::valueOf) ?: ForceEndPhase.Idle,
        ),
    )
    val uiState = mutableState.asStateFlow()
    private var requestJob: Job? = null

    fun request(occurrenceId: String, stop: suspend (String) -> ForceEndMissionResult) {
        if (mutableState.value.phase != ForceEndPhase.Idle || requestJob?.isActive == true) return
        update(ForceEndMissionUiState(occurrenceId, ForceEndPhase.Processing))
        execute(occurrenceId, stop)
    }

    fun retry(stop: suspend (String) -> ForceEndMissionResult) {
        val current = mutableState.value
        if (current.phase != ForceEndPhase.Failed || requestJob?.isActive == true) return
        val id = current.occurrenceId ?: return
        update(current.copy(phase = ForceEndPhase.Processing))
        execute(id, stop)
    }

    // A retained ViewModel already has a job. A restored one reconciles the saved request
    // against the platform's idempotent terminal transition instead of assuming success.
    fun resumePending(stop: suspend (String) -> ForceEndMissionResult) {
        val current = mutableState.value
        if (current.phase != ForceEndPhase.Processing || requestJob?.isActive == true) return
        current.occurrenceId?.let { execute(it, stop) }
    }

    fun dismiss() {
        if (mutableState.value.phase == ForceEndPhase.Processing) return
        update(ForceEndMissionUiState())
    }

    private fun execute(occurrenceId: String, stop: suspend (String) -> ForceEndMissionResult) {
        requestJob = (coroutineScope ?: viewModelScope).launch {
            val result = try {
                stop(occurrenceId)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                ForceEndMissionResult.Failed
            }
            update(when (result) {
                ForceEndMissionResult.Completed -> ForceEndMissionUiState(occurrenceId, ForceEndPhase.Completed)
                ForceEndMissionResult.Failed -> ForceEndMissionUiState(occurrenceId, ForceEndPhase.Failed)
                ForceEndMissionResult.NoLongerActive -> ForceEndMissionUiState()
            })
        }
    }

    private fun update(state: ForceEndMissionUiState) {
        savedState[OccurrenceKey] = state.occurrenceId
        savedState[PhaseKey] = state.phase.name
        mutableState.value = state
    }

    private companion object {
        const val OccurrenceKey = "forceEndOccurrenceId"
        const val PhaseKey = "forceEndPhase"
    }
}
