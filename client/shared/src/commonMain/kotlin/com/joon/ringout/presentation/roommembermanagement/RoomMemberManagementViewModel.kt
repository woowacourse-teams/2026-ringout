package com.joon.ringout.presentation.roommembermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 실제 모임 상세에서 회원을 조회한다. 회원 추방 API는 아직 연결하지 않는다. */
internal class RoomMemberManagementViewModel(
    initialMembers: List<RoomMemberUiModel> = emptyList(),
    canManageMembers: Boolean = false,
    private val loadRoom: suspend (Long) -> RoomMembershipDetails = {
        throw IllegalStateException("회원 목록 조회를 사용할 수 없어요.")
    },
    private val authSession: AuthSession = AuthSession(),
    private val coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var observedAuthState = authSession.state.value
    private var observedAuthIdentity = authSession.identity.value
    private var activeRoomId: String? = null
    private var requestId = 0L
    private var requestJob: Job? = null
    private val mutableUiState = MutableStateFlow(
        RoomMemberManagementUiState(
            members = initialMembers.toList(),
            canManageMembers = canManageMembers,
            canRemoveMembers = canManageMembers,
        ),
    )
    val uiState = mutableUiState.asStateFlow()

    fun onRouteVisible(
        roomId: String,
        authState: AuthSessionState = authSession.state.value,
        identity: Any? = authSession.identity.value,
    ) {
        if (
            activeRoomId == roomId &&
            authState == observedAuthState &&
            identity === observedAuthIdentity &&
            requestJob != null
        ) return
        activeRoomId = roomId
        observedAuthState = authState
        observedAuthIdentity = identity
        requestMembers(authState, identity)
    }

    fun onAuthSessionChanged(authState: AuthSessionState, identity: Any?) {
        if (authState == observedAuthState && identity === observedAuthIdentity) return
        observedAuthState = authState
        observedAuthIdentity = identity
        invalidateRequest()
        mutableUiState.value = RoomMemberManagementUiState()
    }

    fun onRetry() {
        requestMembers(observedAuthState, observedAuthIdentity)
    }

    fun onRemoveMemberClick(memberId: String) {
        mutableUiState.update { state ->
            val member = state.members.firstOrNull { it.id == memberId }
            if (!state.canRemoveMembers || member == null || member.isOwner) state
            else state.copy(selectedMemberId = member.id)
        }
    }

    fun onDismissRemove() {
        mutableUiState.update { it.copy(selectedMemberId = null) }
    }

    /** 추방 API가 연결되기 전까지 실제 화면에서는 회원 목록을 변경하지 않는다. */
    fun onConfirmRemove() {
        mutableUiState.update { it.copy(selectedMemberId = null) }
    }

    private fun requestMembers(authState: AuthSessionState, identity: Any?) {
        invalidateRequest()
        val numericRoomId = activeRoomId?.toLongOrNull()?.takeIf { it > 0L }
        when {
            numericRoomId == null -> {
                showError("모임 정보가 올바르지 않아요.", canManageMembers = false)
                return
            }

            authState == AuthSessionState.Restoring -> {
                showLoading(canManageMembers = false)
                return
            }

            authState != AuthSessionState.Authenticated || identity == null || !isLiveSession(authState, identity) -> {
                showError("로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요.", canManageMembers = false)
                return
            }
        }

        val currentRequestId = requestId
        showLoading(canManageMembers = false)
        requestJob = scope.launch {
            try {
                val details = loadRoom(numericRoomId)
                check(details.room.id == numericRoomId) { "조회한 모임 ID가 요청과 달라요." }
                if (!isCurrentRequest(currentRequestId, numericRoomId, identity)) return@launch
                if (!details.hasReliableMembers()) {
                    showError("회원 정보를 불러오지 못했어요. 다시 시도해 주세요.", canManageMembers = false)
                    return@launch
                }
                val canManageMembers = details.membershipRole == RoomMembershipRole.OWNER
                mutableUiState.update {
                    it.copy(
                        members = if (canManageMembers) details.members.map { member ->
                            RoomMemberUiModel(
                                id = member.userId.toString(),
                                nickname = member.nickname,
                                joinedDate = null,
                                profileImageUrl = member.profileImageUrl,
                                isOwner = false,
                            )
                        } else emptyList(),
                        canManageMembers = canManageMembers,
                        canRemoveMembers = false,
                        isLoading = false,
                        errorMessage = if (canManageMembers) null else "방장만 회원을 관리할 수 있어요.",
                        selectedMemberId = null,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentRequest(currentRequestId, numericRoomId, identity)) return@launch
                showError(memberLoadError(error), canManageMembers = false)
            }
        }
    }

    private fun isCurrentRequest(currentRequestId: Long, numericRoomId: Long, identity: Any?): Boolean =
        requestId == currentRequestId &&
            activeRoomId?.toLongOrNull() == numericRoomId &&
            observedAuthState == AuthSessionState.Authenticated &&
            observedAuthIdentity === identity &&
            isLiveSession(AuthSessionState.Authenticated, identity)

    private fun isLiveSession(authState: AuthSessionState?, identity: Any?): Boolean =
        authState == AuthSessionState.Authenticated &&
            identity != null &&
            authSession.state.value == authState &&
            authSession.identity.value === identity

    private fun invalidateRequest() {
        requestId += 1L
        requestJob?.cancel()
        requestJob = null
    }

    private fun showLoading(canManageMembers: Boolean) {
        mutableUiState.value = RoomMemberManagementUiState(
            canManageMembers = canManageMembers,
            isLoading = true,
        )
    }

    private fun showError(message: String, canManageMembers: Boolean) {
        mutableUiState.value = RoomMemberManagementUiState(
            canManageMembers = canManageMembers,
            errorMessage = message,
        )
    }

    override fun onCleared() {
        invalidateRequest()
        super.onCleared()
    }
}

private fun RoomMembershipDetails.hasReliableMembers(): Boolean =
    room.memberCount > 0 && members.size == room.memberCount &&
        members.map { it.userId }.distinct().size == members.size && members.all { it.userId > 0L }

private fun memberLoadError(error: Throwable): String {
    val apiError = error as? RoomRepositoryException
    return when {
        apiError?.statusCode == 401 || apiError?.code in setOf("AUTH401", "COMMON401", "ROOM401") ->
            "로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요."

        apiError?.statusCode == 403 || apiError?.code in setOf("COMMON403", "ROOM403") ->
            "방장만 회원을 관리할 수 있어요."

        apiError?.statusCode == 404 || apiError?.code in setOf("COMMON404", "ROOM404") ->
            "모임이 존재하지 않거나 삭제됐어요. 모임 목록으로 돌아가 주세요."

        else -> "회원 목록을 불러오지 못했어요. 다시 시도해 주세요."
    }
}
