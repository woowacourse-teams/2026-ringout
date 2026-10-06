package com.joon.ringout.presentation.roommembermanagement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomManagementMember
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class RoomMemberManagementViewModel(
    private val loadMembers: suspend (Long) -> List<RoomManagementMember> = {
        throw IllegalStateException("회원 목록 조회를 사용할 수 없어요.")
    },
    private val kickMember: suspend (Long, Long) -> Unit = { _, _ ->
        throw IllegalStateException("회원 추방을 사용할 수 없어요.")
    },
    private val authSession: AuthSession = AuthSession(),
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var observedAuthState = authSession.state.value
    private var observedAuthIdentity = authSession.identity.value
    private var activeRoomId: String? = null
    private var activeRouteToken: Any? = null
    private var requestId = 0L
    private var removalId = 0L
    private var requestJob: Job? = null
    private var removalJob: Job? = null
    private val mutableUiState = MutableStateFlow(RoomMemberManagementUiState())
    val uiState = mutableUiState.asStateFlow()

    fun onRouteVisible(
        roomId: String,
        authState: AuthSessionState = authSession.state.value,
        identity: Any? = authSession.identity.value,
        routeToken: Any? = null,
    ) {
        val sameRouteAndSession = activeRoomId == roomId &&
            authState == observedAuthState &&
            identity === observedAuthIdentity &&
            routeToken === activeRouteToken
        if (
            sameRouteAndSession &&
            (requestJob?.isActive == true || removalJob?.isActive == true)
        ) return

        if (!sameRouteAndSession) {
            invalidateAllRequests()
            mutableUiState.value = RoomMemberManagementUiState()
        }
        activeRoomId = roomId
        activeRouteToken = routeToken
        observedAuthState = authState
        observedAuthIdentity = identity
        requestMembers(authState, identity)
    }

    fun onAuthSessionChanged(authState: AuthSessionState, identity: Any?) {
        if (authState == observedAuthState && identity === observedAuthIdentity) return
        observedAuthState = authState
        observedAuthIdentity = identity
        invalidateAllRequests()
        mutableUiState.value = RoomMemberManagementUiState()
    }

    fun onRetry() {
        requestMembers(
            authState = observedAuthState,
            identity = observedAuthIdentity,
            preserveMembers = mutableUiState.value.canManageMembers,
        )
    }

    fun onRemoveMemberClick(memberId: String) {
        mutableUiState.update { state ->
            val member = state.members.firstOrNull { it.id == memberId }
            if (
                !state.canRemoveMembers || state.isLoading || state.isRefreshingMembers ||
                state.isRemoving || member == null || member.isOwner
            ) {
                state
            } else {
                state.copy(selectedMemberId = member.id, removeErrorMessage = null)
            }
        }
    }

    fun onDismissRemove() {
        mutableUiState.update { state ->
            if (state.isRemoving) state else state.copy(selectedMemberId = null)
        }
    }

    fun onConfirmRemove() {
        val state = mutableUiState.value
        val member = state.selectedMember ?: return
        val numericRoomId = activeRoomId?.toLongOrNull()?.takeIf { it > 0L } ?: return
        val userId = member.id.toLongOrNull()?.takeIf { it > 0L } ?: return
        val identity = observedAuthIdentity ?: return
        if (
            !state.canManageMembers || !state.canRemoveMembers || state.isLoading ||
            state.isRefreshingMembers || state.isRemoving || member.isOwner ||
            !isLiveSession(observedAuthState, identity)
        ) return

        val currentRemovalId = ++removalId
        invalidateLoadRequest()
        mutableUiState.update {
            it.copy(
                isRemoving = true,
                isRefreshingMembers = false,
                refreshErrorMessage = null,
                removeErrorMessage = null,
            )
        }
        removalJob = scope.launch {
            try {
                kickMember(numericRoomId, userId)
                if (!isCurrentRemoval(currentRemovalId, numericRoomId, identity)) return@launch

                mutableUiState.update { current ->
                    current.copy(
                        members = current.members.filterNot { it.id == userId.toString() },
                        canRemoveMembers = false,
                        isRemoving = false,
                        selectedMemberId = null,
                        removeErrorMessage = null,
                    )
                }
                requestMembers(
                    authState = AuthSessionState.Authenticated,
                    identity = identity,
                    preserveMembers = true,
                    refreshFailureMessage = "추방은 완료됐지만 목록을 갱신하지 못했어요. 다시 시도해 주세요.",
                )
            } catch (error: CancellationException) {
                if (!currentCoroutineContext().isActive ||
                    !isCurrentRemoval(currentRemovalId, numericRoomId, identity)
                ) throw error
                handleRemovalFailure(error, userId, identity)
            } catch (error: Throwable) {
                if (!isCurrentRemoval(currentRemovalId, numericRoomId, identity)) return@launch
                handleRemovalFailure(error, userId, identity)
            } finally {
                if (removalId == currentRemovalId) removalJob = null
            }
        }
    }

    private fun requestMembers(
        authState: AuthSessionState,
        identity: Any?,
        preserveMembers: Boolean = false,
        reconciliationTargetUserId: Long? = null,
        reconciliationMessage: ((Boolean) -> String)? = null,
        refreshFailureMessage: String? = mutableUiState.value.refreshErrorMessage,
    ) {
        invalidateLoadRequest()
        val numericRoomId = activeRoomId?.toLongOrNull()?.takeIf { it > 0L }
        when {
            numericRoomId == null -> {
                showLoadError("모임 정보가 올바르지 않아요.", canRetry = false)
                return
            }

            authState == AuthSessionState.Restoring -> {
                mutableUiState.value = if (preserveMembers) {
                    mutableUiState.value.copy(
                        canRemoveMembers = false,
                        isLoading = false,
                        isRefreshingMembers = true,
                        isRemoving = false,
                        errorMessage = null,
                        refreshErrorMessage = null,
                        selectedMemberId = null,
                    )
                } else {
                    RoomMemberManagementUiState(isLoading = true)
                }
                return
            }

            authState != AuthSessionState.Authenticated || identity == null ||
                !isLiveSession(authState, identity) -> {
                showLoadError(
                    "로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요.",
                    canRetry = false,
                )
                return
            }
        }

        val currentRequestId = requestId
        mutableUiState.value = if (preserveMembers) {
            mutableUiState.value.copy(
                canRemoveMembers = false,
                isLoading = false,
                isRefreshingMembers = true,
                isRemoving = false,
                errorMessage = null,
                refreshErrorMessage = null,
                removeErrorMessage = if (preserveMembers) mutableUiState.value.removeErrorMessage else null,
                selectedMemberId = null,
            )
        } else {
            RoomMemberManagementUiState(isLoading = true)
        }
        requestJob = scope.launch {
            try {
                val members = loadMembers(numericRoomId)
                if (!isCurrentRequest(currentRequestId, numericRoomId, identity)) return@launch
                check(members.hasReliableManagementMembers()) { "회원 정보를 확인할 수 없어요." }
                val uiMembers = members.map(RoomManagementMember::toUiModel)
                val uiMembersExceptOwner = uiMembers.filterNot { it.isOwner }
                val removeMessage = when {
                    reconciliationTargetUserId != null && reconciliationMessage != null ->
                        reconciliationMessage(members.any { it.userId == reconciliationTargetUserId })

                    preserveMembers -> mutableUiState.value.removeErrorMessage
                    else -> null
                }
                mutableUiState.value = RoomMemberManagementUiState(
                    members = uiMembersExceptOwner,
                    canManageMembers = true,
                    canRemoveMembers = true,
                    removeErrorMessage = removeMessage,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentRequest(currentRequestId, numericRoomId, identity)) return@launch
                if (preserveMembers) {
                    mutableUiState.update {
                        it.copy(
                            canRemoveMembers = false,
                            isLoading = false,
                            isRefreshingMembers = false,
                            refreshErrorMessage = refreshFailureMessage
                                ?: "회원 목록을 갱신하지 못했어요. 다시 시도해 주세요.",
                            selectedMemberId = null,
                        )
                    }
                } else {
                    val (message, canRetry) = memberLoadError(error)
                    showLoadError(message, canRetry)
                }
            } finally {
                if (requestId == currentRequestId) requestJob = null
            }
        }
    }

    private fun handleRemovalFailure(
        error: Throwable,
        userId: Long,
        identity: Any,
    ) {
        val apiError = error as? RoomRepositoryException
        when {
            apiError?.code in setOf("ROOM400", "USER404") -> {
                mutableUiState.update {
                    it.copy(
                        isRemoving = false,
                        selectedMemberId = null,
                        removeErrorMessage = "회원 상태가 변경됐어요. 목록을 확인해 주세요.",
                    )
                }
                requestMembers(
                    authState = AuthSessionState.Authenticated,
                    identity = identity,
                    preserveMembers = true,
                    reconciliationTargetUserId = userId,
                    reconciliationMessage = { stillPresent ->
                        if (stillPresent) {
                            "회원 상태가 변경됐어요. 목록에서 회원을 확인한 뒤 다시 시도해 주세요."
                        } else {
                            "회원 상태가 변경됐어요. 현재 목록을 확인해 주세요."
                        }
                    },
                )
            }

            apiError != null && apiError.isManagementAccessError() -> {
                showLoadError(apiError.managementAccessMessage(), canRetry = false)
            }

            apiError == null -> {
                mutableUiState.update {
                    it.copy(
                        isRemoving = false,
                        selectedMemberId = null,
                        removeErrorMessage = "추방 요청 결과를 확인하고 있어요. 회원 목록을 갱신해 주세요.",
                    )
                }
                reconcileRemovalResult(userId, identity)
            }

            else -> {
                mutableUiState.update {
                    it.copy(
                        isRemoving = false,
                        selectedMemberId = null,
                        removeErrorMessage = "회원 추방에 실패했어요. 다시 시도해 주세요.",
                    )
                }
            }
        }
    }

    private fun reconcileRemovalResult(userId: Long, identity: Any) {
        requestMembers(
            authState = AuthSessionState.Authenticated,
            identity = identity,
            preserveMembers = true,
            reconciliationTargetUserId = userId,
            reconciliationMessage = { stillPresent ->
                if (stillPresent) {
                    "추방 요청 결과를 확인할 수 없어요. 대상이 목록에 남아 있어요. 확인 후 다시 시도해 주세요."
                } else {
                    "추방 요청 결과를 확인할 수 없지만 대상은 현재 목록에 없어요."
                }
            },
        )
    }

    private fun isCurrentRequest(currentRequestId: Long, roomId: Long, identity: Any?): Boolean =
        requestId == currentRequestId &&
            activeRoomId?.toLongOrNull() == roomId &&
            observedAuthState == AuthSessionState.Authenticated &&
            observedAuthIdentity === identity &&
            isLiveSession(AuthSessionState.Authenticated, identity)

    private fun isCurrentRemoval(currentRemovalId: Long, roomId: Long, identity: Any?): Boolean =
        removalId == currentRemovalId &&
            activeRoomId?.toLongOrNull() == roomId &&
            observedAuthState == AuthSessionState.Authenticated &&
            observedAuthIdentity === identity &&
            isLiveSession(AuthSessionState.Authenticated, identity)

    private fun isLiveSession(authState: AuthSessionState?, identity: Any?): Boolean =
        authState == AuthSessionState.Authenticated &&
            identity != null &&
            authSession.state.value == authState &&
            authSession.identity.value === identity

    private fun invalidateLoadRequest() {
        requestId += 1L
        requestJob?.cancel()
        requestJob = null
    }

    private fun invalidateAllRequests() {
        invalidateLoadRequest()
        removalId += 1L
        removalJob?.cancel()
        removalJob = null
    }

    private fun showLoadError(message: String, canRetry: Boolean) {
        mutableUiState.value = RoomMemberManagementUiState(
            errorMessage = message,
            canRetryLoad = canRetry,
        )
    }

    override fun onCleared() {
        invalidateAllRequests()
        super.onCleared()
    }
}

private fun List<RoomManagementMember>.hasReliableManagementMembers(): Boolean =
    all { member ->
        member.userId > 0L &&
            member.joinedDateOrNull() != null &&
            member.membershipRole in setOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER)
    } && map { it.userId }.distinct().size == size

private fun RoomManagementMember.toUiModel(): RoomMemberUiModel = RoomMemberUiModel(
    id = userId.toString(),
    nickname = nickname,
    joinedDate = checkNotNull(joinedDateOrNull()) { "회원 가입일을 확인할 수 없어요." },
    profileImageUrl = profileImageUrl,
    isOwner = membershipRole == RoomMembershipRole.OWNER,
)

private fun RoomManagementMember.joinedDateOrNull(): String? = runCatching {
    val date = joinedAt.takeIf { it.length > 10 && it[10] == 'T' }?.take(10)
        ?: return@runCatching null
    MissionDate.parse(date).iso8601
}.getOrNull()

private fun memberLoadError(error: Throwable): Pair<String, Boolean> {
    val apiError = error as? RoomRepositoryException
    return when {
        apiError.isAuthenticationError() ->
            "로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요." to false

        apiError.isManagementPermissionError() ->
            "방장만 회원을 관리할 수 있어요." to false

        apiError.isMissingRoomError() ->
            "모임이 존재하지 않거나 삭제됐어요. 모임 목록으로 돌아가 주세요." to false

        else -> "회원 목록을 불러오지 못했어요. 다시 시도해 주세요." to true
    }
}

private fun RoomRepositoryException?.isAuthenticationError(): Boolean =
    this?.statusCode == 401 || this?.code in setOf("AUTH401", "COMMON401", "ROOM401")

private fun RoomRepositoryException?.isManagementPermissionError(): Boolean =
    this?.statusCode == 403 || this?.code in setOf("COMMON403", "MEMBER403", "ROOM403")

private fun RoomRepositoryException?.isMissingRoomError(): Boolean =
    this?.code in setOf("COMMON404", "ROOM404")

private fun RoomRepositoryException?.isManagementAccessError(): Boolean =
    isAuthenticationError() || isManagementPermissionError() || isMissingRoomError()

private fun RoomRepositoryException.managementAccessMessage(): String =
    when {
        isAuthenticationError() -> "로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요."
        isManagementPermissionError() -> "방장만 회원을 관리할 수 있어요."
        else -> "모임이 존재하지 않거나 삭제됐어요. 모임 목록으로 돌아가 주세요."
    }
