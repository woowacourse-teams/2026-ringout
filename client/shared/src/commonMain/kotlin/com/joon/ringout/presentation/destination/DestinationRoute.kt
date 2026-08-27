package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.toAnalyticsLoginStateOrNull

@Composable
internal fun DestinationRoute(
    viewModel: DestinationViewModel,
    initialSelection: DestinationSelection,
    requestId: Long,
    authSessionState: AuthSessionState,
    productAnalyticsRecorder: ProductAnalyticsRecorder,
    isActive: Boolean,
    onBackClick: () -> Unit,
    onSavedDestinationConfirmClick: (SavedDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    val analyticsLoginState = authSessionState.toAnalyticsLoginStateOrNull()

    DestinationMapScreen(
        initialSelection = initialSelection,
        requestCurrentLocationOnStart = false,
        isAuthenticated = authSessionState == AuthSessionState.Authenticated,
        onEntered = { if (isActive) viewModel.onScreenEntered() },
        onBackClick = { if (isActive) onBackClick() },
        onConfirmClick = { destination ->
            if (isActive && analyticsLoginState != null) {
                viewModel.save(destination, requestId, analyticsLoginState)
            }
        },
        onSavedDestinationConfirmClick = { destination ->
            if (isActive) onSavedDestinationConfirmClick(destination)
        },
        savedDestinations = uiState.destinations,
        onSavedDestinationRename = { id, nickname ->
            if (isActive) viewModel.rename(id, nickname)
        },
        onSavedDestinationDeleteClick = { id ->
            if (isActive) viewModel.delete(id)
        },
        onSavedDestinationSelected = { source ->
            if (isActive && analyticsLoginState != null) {
                productAnalyticsRecorder.recordDestinationSelected(source, analyticsLoginState)
            }
        },
        isSaveInProgress = uiState.isSaving,
        isDestinationActionEnabled = isActive && analyticsLoginState != null,
        isBackHandlerEnabled = isActive,
        modifier = modifier,
    )
}
