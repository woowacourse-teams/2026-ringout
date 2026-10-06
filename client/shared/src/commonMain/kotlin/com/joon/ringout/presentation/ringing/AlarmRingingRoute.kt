package com.joon.ringout.presentation.ringing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
internal fun AlarmRingingRoute(
    limitMinutes: Int,
    destinationName: String,
    onDismissAndNavigateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = viewModel { AlarmRingingViewModel() }
    val clock by viewModel.clock.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.startClock()
        onPauseOrDispose { viewModel.stopClock() }
    }

    AlarmRingingScreen(
        currentTime = clock.time,
        dateText = clock.dateText,
        limitMinutes = limitMinutes,
        destinationName = destinationName,
        onDismissAndNavigateClick = onDismissAndNavigateClick,
        modifier = modifier,
    )
}
