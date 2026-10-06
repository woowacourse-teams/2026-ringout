package com.joon.ringout.presentation.ringing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class AlarmRingingViewModel(
    private val currentClock: () -> AlarmRingingClockUiState = ::currentAlarmRingingClock,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val mutableClock = MutableStateFlow(currentClock())
    val clock = mutableClock.asStateFlow()

    private val scope = coroutineScope ?: viewModelScope
    private var clockJob: Job? = null

    fun startClock() {
        mutableClock.value = currentClock()
        if (clockJob?.isActive == true) return
        clockJob = scope.launch {
            while (isActive) {
                delay(ClockRefreshIntervalMillis)
                mutableClock.value = currentClock()
            }
        }
    }

    fun stopClock() {
        clockJob?.cancel()
        clockJob = null
    }
}

private const val ClockRefreshIntervalMillis = 1_000L
