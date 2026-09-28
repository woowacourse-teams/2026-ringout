package com.joon.ringout.presentation.social

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.social.model.RoomUiModel
import com.joon.ringout.presentation.social.model.SocialUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SocialViewModel(
    private val loadRooms: suspend () -> Result<List<RoomUiModel>> = {
        Result.success(emptyList())
    },
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    var uiState by mutableStateOf(SocialUiState())
        private set

    private var lastAuthSessionState: AuthSessionState? = null
    private var roomsRequestId = 0L
    private var roomsJob: Job? = null

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
        requestRooms()
    }

    internal fun onRetryRooms() {
        if (lastAuthSessionState != null) requestRooms()
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
                    allRoomsErrorMessage = error.message ?: RoomListLoadErrorMessage,
                )
            }
        }
    }
}

internal const val RoomListLoadErrorMessage = "모임 목록을 불러오지 못했습니다."
