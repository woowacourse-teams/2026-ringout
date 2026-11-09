package com.joon.ringout.presentation.arrivalshare
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.joon.ringout.presentation.navigation.NavigationBackAction
import com.joon.ringout.presentation.navigation.LocalNavigationBackActionRegistrar
import com.joon.ringout.presentation.destination.PlatformBackHandler
@Composable internal fun ArrivalShareRoute(viewModel: ArrivalShareViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    NavigationBackAction(onBack)
    if (LocalNavigationBackActionRegistrar.current == null) PlatformBackHandler(onBack = onBack)
    ArrivalShareScreen(state, onBack, viewModel::setShowTime, viewModel::setShowDate, viewModel::retry)
}
