package com.joon.ringout.presentation.arrival

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.presentation.destination.PlatformBackHandler
import com.joon.ringout.presentation.navigation.LocalNavigationBackActionRegistrar
import com.joon.ringout.presentation.navigation.NavigationBackAction

@Composable
internal fun ArrivalRoute(
    viewModel: ArrivalViewModel,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    onShare: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnConfirmed by rememberUpdatedState(onConfirmed)

    NavigationBackAction(viewModel::confirm)
    if (LocalNavigationBackActionRegistrar.current == null) {
        PlatformBackHandler(onBack = viewModel::confirm)
    }
    LaunchedEffect(viewModel, uiState.isAcknowledged) {
        if (uiState.isAcknowledged) currentOnConfirmed()
    }

    ArrivalScreen(
        uiState = uiState,
        onStartDay = viewModel::confirm,
        onShare = onShare,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
