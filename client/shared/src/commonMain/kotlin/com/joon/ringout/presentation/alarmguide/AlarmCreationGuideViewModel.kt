package com.joon.ringout.presentation.alarmguide

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class AlarmCreationGuideStep { AddAlarm, Time, Weekdays, Destination, Interval, Sound, CreateAlarm }
internal enum class AlarmCreationGuideSurface { Home, Editor, Other }

internal data class AlarmCreationGuideUiState(
    val step: AlarmCreationGuideStep? = null,
    val isFinishing: Boolean = false,
)

/** One guide spans Home and the existing alarm editor without owning an alarm draft. */
internal class AlarmCreationGuideViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(AlarmCreationGuideUiState())
    val uiState = mutableUiState.asStateFlow()
    private var currentSurface = AlarmCreationGuideSurface.Other
    private var lastPresentedSurface = AlarmCreationGuideSurface.Other
    private var canAdvance = false

    fun onContextChanged(
        isPending: Boolean,
        surface: AlarmCreationGuideSurface,
        canPresent: Boolean,
    ) {
        currentSurface = surface
        canAdvance = isPending && canPresent && surface != AlarmCreationGuideSurface.Other
        if (!isPending) {
            mutableUiState.value = AlarmCreationGuideUiState()
            lastPresentedSurface = AlarmCreationGuideSurface.Other
            return
        }
        if (uiState.value.isFinishing || !canPresent) return
        val state = uiState.value
        if (surface == AlarmCreationGuideSurface.Home &&
            (state.step == null || lastPresentedSurface != AlarmCreationGuideSurface.Home)
        ) {
            // Returning to Home starts over, but a pending Home-to-editor transition does not.
            mutableUiState.value = state.copy(step = AlarmCreationGuideStep.AddAlarm)
        }
        lastPresentedSurface = surface
    }

    /** Advance the explanation without reading or changing the alarm draft. */
    fun onNext(step: AlarmCreationGuideStep): Boolean {
        val state = uiState.value
        val expectedSurface = if (step == AlarmCreationGuideStep.AddAlarm) {
            AlarmCreationGuideSurface.Home
        } else {
            AlarmCreationGuideSurface.Editor
        }
        if (!canAdvance || currentSurface != expectedSurface || state.step != step || state.isFinishing) return false
        mutableUiState.value = when (step) {
            AlarmCreationGuideStep.AddAlarm -> state.copy(step = AlarmCreationGuideStep.Time)
            AlarmCreationGuideStep.Time -> state.copy(step = AlarmCreationGuideStep.Weekdays)
            AlarmCreationGuideStep.Weekdays -> state.copy(step = AlarmCreationGuideStep.Destination)
            AlarmCreationGuideStep.Destination -> state.copy(step = AlarmCreationGuideStep.Interval)
            AlarmCreationGuideStep.Interval -> state.copy(step = AlarmCreationGuideStep.Sound)
            AlarmCreationGuideStep.Sound -> state.copy(step = AlarmCreationGuideStep.CreateAlarm)
            AlarmCreationGuideStep.CreateAlarm -> state.copy(isFinishing = true)
        }
        return true
    }

    /** Keep a terminal state while persistence is pending or awaiting a retry. */
    fun finish(): Boolean {
        val state = uiState.value
        if (!canAdvance || state.step == null || state.isFinishing) return false
        mutableUiState.value = state.copy(isFinishing = true)
        return true
    }
}
