package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.toAnalyticsLoginStateOrNull
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.destination.SavedDestination

@Composable
internal fun DestinationRoute(
    viewModel: DestinationViewModel,
    initialSelection: DestinationSelection?,
    requestId: Long,
    authSessionState: AuthSessionState,
    productAnalyticsRecorder: ProductAnalyticsRecorder,
    isActive: Boolean,
    onBackClick: () -> Unit,
    onSavedDestinationConfirmClick: (SavedDestination) -> Unit,
    onConfirmWithoutSave: (DestinationSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState = viewModel.uiState
    val analyticsLoginState = authSessionState.toAnalyticsLoginStateOrNull()
    // A new fix must not change the initial camera of an already displayed map.
    val entrySelection = remember(viewModel, requestId) {
        viewModel.initialCurrentLocation() ?: initialSelection ?: DefaultDestinationSelection
    }

    DestinationMapScreen(
        initialSelection = entrySelection,
        requestCurrentLocationOnStart = true,
        onCurrentLocationResolved = viewModel::onCurrentLocationResolved,
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
        onConfirmWithoutSave = { destination ->
            if (isActive) onConfirmWithoutSave(destination.withAddressAsName())
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
        isDestinationActionEnabled = isActive && analyticsLoginState != null && uiState.hasLoadedDestinations,
        isBackHandlerEnabled = isActive,
        modifier = modifier,
    )
}
