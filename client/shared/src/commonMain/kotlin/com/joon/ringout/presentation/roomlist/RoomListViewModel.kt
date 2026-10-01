package com.joon.ringout.presentation.roomlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.presentation.roomlist.model.RoomListUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomListViewModel(
    private val loadRooms: suspend () -> Result<List<RoomUiModel>>,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    var uiState by mutableStateOf(RoomListUiState())
        private set

    private var lastAuthSessionState: AuthSessionState? = null
    private var roomsRequestId = 0L
    private var roomsJob: Job? = null

    internal val isRoomListUninitialized: Boolean
        get() = lastAuthSessionState == null

    internal fun onRouteVisible(authSessionState: AuthSessionState) {
        if (lastAuthSessionState == authSessionState) return
        val hadRouteState = lastAuthSessionState != null
        lastAuthSessionState = authSessionState
        uiState = uiState.copy(
            allRooms = if (hadRouteState) emptyList() else uiState.allRooms,
            joinedRooms = emptyList(),
            allRoomsErrorMessage = if (hadRouteState) null else uiState.allRoomsErrorMessage,
            isAuthenticated = authSessionState == AuthSessionState.Authenticated,
        )
        if (authSessionState != AuthSessionState.Restoring) requestRooms()
    }

    internal fun onRetryRooms() {
        if (lastAuthSessionState != null && lastAuthSessionState != AuthSessionState.Restoring) requestRooms()
    }

    private fun requestRooms() {
        val requestId = ++roomsRequestId
        roomsJob?.cancel()
        uiState = uiState.copy(
            isLoadingAllRooms = true,
            allRoomsErrorMessage = null,
        )
        roomsJob = scope.launch {
            try {
                val result = loadRooms()
                val rooms = result.getOrThrow()
                if (requestId != roomsRequestId) return@launch
                uiState = uiState.copy(
                    allRooms = rooms,
                    joinedRooms = rooms.filter { it.isJoined },
                    isLoadingAllRooms = false,
                    allRoomsErrorMessage = null,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (requestId != roomsRequestId) return@launch
                uiState = uiState.copy(
                    isLoadingAllRooms = false,
                    allRoomsErrorMessage = RoomListLoadErrorMessage,
                )
            }
        }
    }
}

internal const val RoomListLoadErrorMessage = "모임 목록을 불러오는 중 문제가 발생했어요."
