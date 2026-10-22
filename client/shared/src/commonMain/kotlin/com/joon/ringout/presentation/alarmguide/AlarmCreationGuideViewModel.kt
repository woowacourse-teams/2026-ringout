package com.joon.ringout.presentation.alarmguide

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class AlarmCreationGuideStep { AddAlarm, Time, Weekdays, Destination, Interval, Sound, CreateAlarm }
internal enum class AlarmCreationGuideSurface { Home, Editor, Destination, Sound, Other }
internal enum class AlarmCreationGuideSelection { Destination, Sound }

internal data class AlarmCreationGuideUiState(
    val step: AlarmCreationGuideStep? = null,
    val selection: AlarmCreationGuideSelection? = null,
    val isFinishing: Boolean = false,
)

/** One guide spans Home and the existing alarm editor without owning an alarm draft. */
internal class AlarmCreationGuideViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(AlarmCreationGuideUiState())
    val uiState = mutableUiState.asStateFlow()
    private var previousSurface = AlarmCreationGuideSurface.Other

    fun onContextChanged(
        isPending: Boolean,
        surface: AlarmCreationGuideSurface,
        canPresent: Boolean,
        hasDestination: Boolean,
    ) {
        if (!isPending) {
            mutableUiState.value = AlarmCreationGuideUiState()
            previousSurface = surface
            return
        }
        if (uiState.value.isFinishing) return
        if (!canPresent) {
            // A priority screen may interrupt selection before its navigation is observed.
            // Retain the selection surface until the editor can safely resume the guide.
            if (surface == AlarmCreationGuideSurface.Destination || surface == AlarmCreationGuideSurface.Sound) {
                previousSurface = surface
            }
            return
        }
        val state = uiState.value
        when {
            surface == AlarmCreationGuideSurface.Home -> {
                // A discarded or process-restored editor starts again at the real Home button.
                mutableUiState.value = state.copy(step = AlarmCreationGuideStep.AddAlarm, selection = null)
            }
            state.step != null && surface == AlarmCreationGuideSurface.Editor -> {
                when {
                    state.step == AlarmCreationGuideStep.Destination && hasDestination ->
                        onDestinationSelectionFinished(true)
                    state.selection == AlarmCreationGuideSelection.Destination &&
                        previousSurface == AlarmCreationGuideSurface.Destination ->
                        onDestinationSelectionFinished(false)
                    state.selection == AlarmCreationGuideSelection.Sound &&
                        previousSurface == AlarmCreationGuideSurface.Sound -> onSoundSelectionFinished(false)
                }
            }
        }
        previousSurface = surface
    }

    /** Only the currently highlighted target may advance the explanation. */
    fun onTargetPressed(step: AlarmCreationGuideStep): Boolean {
        val state = uiState.value
        if (state.step != step || state.selection != null || state.isFinishing) return false
        mutableUiState.value = when (step) {
            AlarmCreationGuideStep.AddAlarm -> state.copy(step = AlarmCreationGuideStep.Time)
            AlarmCreationGuideStep.Time -> state.copy(step = AlarmCreationGuideStep.Weekdays)
            AlarmCreationGuideStep.Weekdays -> state.copy(step = AlarmCreationGuideStep.Destination)
            AlarmCreationGuideStep.Destination -> state.copy(selection = AlarmCreationGuideSelection.Destination)
            AlarmCreationGuideStep.Interval -> state.copy(step = AlarmCreationGuideStep.Sound)
            AlarmCreationGuideStep.Sound -> state.copy(selection = AlarmCreationGuideSelection.Sound)
            AlarmCreationGuideStep.CreateAlarm -> state // The existing save result completes the guide.
        }
        return true
    }

    fun onDestinationSelectionFinished(hasDestination: Boolean) {
        val state = uiState.value
        if (state.step != AlarmCreationGuideStep.Destination || state.isFinishing) return
        mutableUiState.value = state.copy(
            step = if (hasDestination) AlarmCreationGuideStep.Interval else AlarmCreationGuideStep.Destination,
            selection = null,
        )
    }

    fun onSoundSelectionFinished(selected: Boolean) {
        val state = uiState.value
        if (state.selection != AlarmCreationGuideSelection.Sound || state.isFinishing) return
        mutableUiState.value = state.copy(
            step = if (selected) AlarmCreationGuideStep.CreateAlarm else AlarmCreationGuideStep.Sound,
            selection = null,
        )
    }

    fun onAlarmCreated(): Boolean = if (uiState.value.step == AlarmCreationGuideStep.CreateAlarm) finish() else false

    /** Keep a terminal state while persistence is pending or awaiting a retry. */
    fun finish(): Boolean {
        val state = uiState.value
        if (state.step == null || state.isFinishing) return false
        mutableUiState.value = state.copy(selection = null, isFinishing = true)
        return true
    }
}
