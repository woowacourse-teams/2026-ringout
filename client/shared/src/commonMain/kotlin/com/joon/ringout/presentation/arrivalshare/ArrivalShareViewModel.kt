package com.joon.ringout.presentation.arrivalshare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.arrival.ArrivalResultRepository
import com.joon.ringout.domain.missionhistory.GetAlarmRecordGroup
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.MissionResult
import com.joon.ringout.domain.missionhistory.toShareableAlarmRecord
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
    val arrivalTime: String? = null,
    val wakeEpochMillis: Long? = null,
    val arrivalEpochMillis: Long? = null,
    val showTime: Boolean = true,
    val showDate: Boolean = true,
    val photoPath: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val useBlackText: Boolean = false,
)

internal data class ArrivalShareRecordSelection(
    val date: MissionDate,
    val groupKey: String,
    val successRecordKey: String,
)

internal expect fun arrivalShareLocalLabels(epochMillis: Long): Pair<String, String>

internal class ArrivalShareViewModel(
    val occurrenceId: String,
    private val repository: ArrivalResultRepository,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    private val scope: CoroutineScope? = null,
    private val recordLookup: GetAlarmRecordGroup? = null,
    private val recordSelection: ArrivalShareRecordSelection? = null,
    private val format: (Long) -> Pair<String, String> = ::arrivalShareLocalLabels,
) : ViewModel() {
    private val state = MutableStateFlow(ArrivalShareUiState(
        useBlackText = savedState["useBlackText"] ?: false,
        photoPath = savedState["photoPath"],
        showTime = savedState["showTime"] ?: true,
        showDate = savedState["showDate"] ?: true,
    ))
    val uiState = state.asStateFlow()
    private var job: Job? = null
    private var restoredDraft: ArrivalDraft? = null
    init { retry() }

    fun retry() {
        if (job?.isActive == true) return
        state.update { it.copy(isLoading = true, error = null) }
        job = (scope ?: viewModelScope).launch {
            try {
                val selection = recordSelection
                val date: String
                val wakeAt: Long?
                val arrivedAt: Long?
                if (selection != null) {
                    val group = recordLookup?.byDateAndKey(selection.date, selection.groupKey)
                    val data = group?.toShareableAlarmRecord(selection.successRecordKey)
                    if (data == null || (data.occurrenceId != null && data.occurrenceId != occurrenceId)) {
                        state.update { it.copy(isLoading = false, error = "공유할 수 있는 기록을 찾을 수 없어요.") }
                        return@launch
                    }
                    wakeAt = data.wakeAtEpochMillis
                    arrivedAt = data.arrivedAtEpochMillis
                    date = selection.date.iso8601.replace('-', '.')
                } else {
                    val result = repository.getResult(occurrenceId)
                    if (result == null) {
                        state.update { it.copy(isLoading = false, error = "도착 기록을 찾을 수 없어요.") }
                        return@launch
                    }
                    val group = recordLookup?.byOccurrenceId(occurrenceId)
                    val first = group?.entries?.firstOrNull()
                    wakeAt = first?.ringingScheduledAtEpochMillis ?: first?.ringingStartedAtEpochMillis
                    val selected = group?.entries?.firstOrNull { it.occurrenceId == occurrenceId && it.result == MissionResult.SUCCESS }
                    arrivedAt = selected?.missionCompletedAtEpochMillis ?: result.completedAtEpochMillis
                    date = group?.date?.iso8601?.replace('-', '.') ?: result.completedLocalDate.iso8601.replace('-', '.')
                }
                val wakeLabel = labelFor(wakeAt, "wake", restoredDraft?.wakeEpochMillis, restoredDraft?.wakeLabel)
                val arrivalLabel = labelFor(arrivedAt, "arrival", restoredDraft?.arrivalEpochMillis, restoredDraft?.arrivalLabel)
                state.update { it.copy(isLoading = false, date = date, time = wakeLabel, arrivalTime = arrivalLabel,
                    wakeEpochMillis = wakeAt, arrivalEpochMillis = arrivedAt) }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                state.update { it.copy(isLoading = false, error = "도착 기록을 불러오지 못했어요. 다시 시도해 주세요.") }
            }
        }
    }

    private fun labelFor(epoch: Long?, prefix: String, draftEpoch: Long?, draftLabel: String?): String? {
        if (epoch == null) return null
        val savedEpoch = savedState.get<Long>("${prefix}EpochMillis")
        val savedLabel = savedState.get<String>("${prefix}Label")
        val label = when {
            draftEpoch == epoch && draftLabel != null -> draftLabel
            savedEpoch == epoch && savedLabel != null -> savedLabel
            else -> format(epoch).second
        }
        savedState["${prefix}EpochMillis"] = epoch
        savedState["${prefix}Label"] = label
        return label
    }

    fun setShowTime(value: Boolean) {
        if (state.value.busy || (state.value.time == null && state.value.arrivalTime == null)) return
        savedState["showTime"] = value
        state.update { it.copy(showTime = value) }
    }
    fun setShowDate(value: Boolean) {
        if (state.value.busy) return
        savedState["showDate"] = value
        state.update { it.copy(showDate = value) }
    }
    fun toggleTextColor() {
        if (state.value.busy) return
        val useBlackText = !state.value.useBlackText
        savedState["useBlackText"] = useBlackText
        state.update { it.copy(useBlackText = useBlackText) }
    }
    private var draftRestored = false
    fun restoreDraft(draft: ArrivalDraft?) {
        if (draftRestored) return
        draftRestored = true
        if (draft == null) return
        restoredDraft = draft
        savedState["useBlackText"] = draft.useBlackText
        savedState["photoPath"] = draft.photoPath
        savedState["showTime"] = draft.showTime
        savedState["showDate"] = draft.showDate
        // Date and time in older drafts were display snapshots; current records are authoritative.
        state.update { current ->
            val wake = if (current.wakeEpochMillis != null && current.wakeEpochMillis == draft.wakeEpochMillis && draft.wakeLabel != null)
                labelFor(current.wakeEpochMillis, "wake", draft.wakeEpochMillis, draft.wakeLabel) else current.time
            val arrival = if (current.arrivalEpochMillis != null && current.arrivalEpochMillis == draft.arrivalEpochMillis && draft.arrivalLabel != null)
                labelFor(current.arrivalEpochMillis, "arrival", draft.arrivalEpochMillis, draft.arrivalLabel) else current.arrivalTime
            current.copy(useBlackText = draft.useBlackText, photoPath = draft.photoPath, showTime = draft.showTime, showDate = draft.showDate,
                time = wake, arrivalTime = arrival)
        }
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
