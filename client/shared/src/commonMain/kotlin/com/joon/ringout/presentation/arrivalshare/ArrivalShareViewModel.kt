package com.joon.ringout.presentation.arrivalshare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class ArrivalShareUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val date: String = "",
    val time: String? = null,
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val photoPath: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
)

internal expect fun arrivalShareLocalLabels(epochMillis: Long): Pair<String, String>

internal class ArrivalShareViewModel(
    val occurrenceId: String,
    private val repository: ArrivalResultRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val scope: CoroutineScope? = null,
    private val format: (Long) -> Pair<String, String> = ::arrivalShareLocalLabels,
) : ViewModel() {
    private val state = MutableStateFlow(ArrivalShareUiState(
        photoPath = savedState["photoPath"],
        showTime = savedState["showTime"] ?: true,
        showDate = savedState["showDate"] ?: true,
    ))
    val uiState = state.asStateFlow()
    private var job: Job? = null
    init { retry() }
    fun retry() {
        if (job?.isActive == true) return
        state.update { it.copy(isLoading = true, error = null) }
        job = (scope ?: viewModelScope).launch {
            try {
                val result = repository.getResult(occurrenceId)
                if (result == null) {
                    state.update { it.copy(isLoading = false, error = "도착 기록을 찾을 수 없어요.") }
                    return@launch
                }
                val labels = result.alarmDismissedAtEpochMillis?.let(format)
                // Keep the first displayed local labels stable across editor recreation/time-zone changes.
                val date = savedState.get<String>("date") ?: (labels?.first ?: result.completedLocalDate.iso8601.replace('-', '.'))
                val time = savedState.get<String>("time") ?: labels?.second
                savedState["date"] = date
                savedState["time"] = time
                state.update { it.copy(isLoading = false, date = date, time = time) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false, error = "도착 기록을 불러오지 못했어요. 다시 시도해 주세요.") }
            }
        }
    }
    fun setShowTime(value: Boolean) {
        if (state.value.busy || state.value.time == null) return
        savedState["showTime"] = value
        state.update { it.copy(showTime = value) }
    }
    fun setShowDate(value: Boolean) {
        if (state.value.busy) return
        savedState["showDate"] = value
        state.update { it.copy(showDate = value) }
    }
    private var draftRestored = false
    fun restoreDraft(draft: ArrivalDraft?) {
        if (draftRestored) return
        draftRestored = true
        if (draft == null) return
        savedState["photoPath"] = draft.photoPath
        savedState["showTime"] = draft.showTime
        savedState["showDate"] = draft.showDate
        if (draft.date.isNotEmpty()) savedState["date"] = draft.date
        if (draft.time != null) savedState["time"] = draft.time
        state.update { it.copy(photoPath = draft.photoPath, showTime = draft.showTime, showDate = draft.showDate,
            date = draft.date.ifEmpty { it.date }, time = draft.time ?: it.time) }
    }
    fun beginMedia(): Boolean {
        val current = state.value
        if (current.busy || current.isLoading || current.error != null) return false
        state.update { it.copy(busy = true, message = null) }
        return true
    }
    fun finishMedia(result: ArrivalMediaResult) {
        when (result) {
            is ArrivalMediaResult.Photo -> {
                savedState["photoPath"] = result.path
                state.update { it.copy(photoPath = result.path, busy = false, message = null) }
            }
            is ArrivalMediaResult.Done -> state.update { it.copy(busy = false, message = result.message) }
            is ArrivalMediaResult.Failed -> state.update { it.copy(busy = false, message = result.message) }
        }
    }

}
