package com.joon.ringout.presentation.roomlist.roomdetail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.roomlist.model.RoomMutationEntryIds
import com.joon.ringout.presentation.roomlist.model.RoomMutationSource
import com.joon.ringout.presentation.roomlist.model.RoomMutationType
import com.joon.ringout.presentation.roomlist.model.RoomMutationUiState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

@Composable
internal fun RoomDetailRoute(
    room: RoomUiModel?,
    isLoading: Boolean,
    errorMessage: String?,
    authSessionState: AuthSessionState,
    sessionIdentity: Any?,
    onRouteVisible: (AuthSessionState, Any?) -> Unit,
    mutationState: RoomMutationUiState,
    onMutationSourceVisible: (Long) -> Unit,
    onMutationSourceHidden: (Long) -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    onJoinRoom: (RoomMutationSource) -> Unit,
    onRetryRooms: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sourceEntryId = rememberSaveable(room?.id) { RoomMutationEntryIds.next() }
    val source = room?.let {
        RoomMutationSource(
            entryId = checkNotNull(sourceEntryId),
            type = RoomMutationType.Join,
            roomId = it.id,
        )
    }
    LaunchedEffect(onRouteVisible, authSessionState, sessionIdentity) {
        onRouteVisible(authSessionState, sessionIdentity)
    }
    LaunchedEffect(source?.entryId, authSessionState, sessionIdentity) {
        source?.let { onMutationSourceVisible(it.entryId) }
    }
    DisposableEffect(source?.entryId) {
        val sourceEntryIdToClear = source?.entryId
        onDispose { sourceEntryIdToClear?.let(onMutationSourceHidden) }
    }

    if (room != null) {
        val currentMutation = mutationState.source == source
        RoomDetailScreen(
            room = room,
            authSessionState = authSessionState,
            onBackClick = onBackClick,
            onLoginClick = onLoginClick,
            onJoinRoom = { source?.let(onJoinRoom) },
            isMutationInProgress = mutationState.isInProgress,
            isJoining = mutationState.isInProgress && currentMutation,
            mutationErrorMessage = mutationState.errorMessage.takeIf { currentMutation },
            isMembershipConfirmed = currentMutation && mutationState.isMembershipConfirmed,
            onRetryRooms = onRetryRooms,
            modifier = modifier,
        )
    } else {
        RoomDetailStatusScreen(
            isLoading = isLoading,
            errorMessage = errorMessage,
            onBackClick = onBackClick,
            onRetry = onRetryRooms,
            modifier = modifier,
        )
    }
}
