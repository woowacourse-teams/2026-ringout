package com.joon.ringout.presentation.destination

import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.first

// Navigation keeps an entering screen STARTED until its transition finishes.
internal suspend fun Lifecycle.awaitDestinationLocationReady(): Boolean =
    currentStateFlow.first { state ->
        state == Lifecycle.State.DESTROYED || state.isAtLeast(Lifecycle.State.RESUMED)
    } != Lifecycle.State.DESTROYED
