package com.joon.ringout.presentation.roomhome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.missionhistory.plusDays
import com.joon.ringout.domain.missionhistory.weekDates
import com.joon.ringout.domain.missionhistory.yearMonth
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomScheduleClock
import com.joon.ringout.domain.room.RoomRecords
import com.joon.ringout.presentation.roomlist.model.toRoomUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 상세 조회 결과와 기기 시각으로 모임 홈 상태를 관리한다. */
internal class RoomHomeViewModel(
    initialState: RoomHomeUiState = RoomHomeUiState(),
    recordsByDate: Map<MissionDate, RoomHomeDayRecordsUiModel> = mapOf(
        initialState.recordsState.selectedDate to RoomHomeDayRecordsUiModel(
            records = initialState.recordsState.records,
            achievedMemberCount = initialState.recordsState.achievedMemberCount,
        ),
    ),
    private val clock: RoomScheduleClock = systemRoomScheduleClock(),
    private val coroutineScope: CoroutineScope? = null,
    private val loadRoom: suspend (Long) -> RoomMembershipDetails = {
        throw IllegalStateException("모임 상세 조회를 사용할 수 없어요.")
    },
    private val authSession: AuthSession = AuthSession(),
    private val loadRecords: suspend (Long, MissionDate) -> RoomRecords = { _, _ ->
        throw IllegalStateException("모임 기록 조회를 사용할 수 없어요.")
    },
    private val deleteRoom: suspend (Long) -> Unit = {
        throw IllegalStateException("모임 삭제를 사용할 수 없어요.")
    },
    private val leaveRoom: suspend (Long) -> Unit = {
        throw IllegalStateException("모임 탈퇴를 사용할 수 없어요.")
    },
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var canViewRecords = initialState.room?.isJoined == true && initialState.recordsState.canViewRecords
    private var localRecords = if (canViewRecords && initialState.recordsState.isDataLoaded) {
        recordsByDate.mapValues { (_, value) -> value.copy(records = value.records.toList()) }
    } else emptyMap()
    private var observedAuthState = authSession.state.value
    private var observedAuthIdentity = authSession.identity.value
    private var activeRoomId: String? = null
    private var activeRouteAuthState: AuthSessionState? = null
    private var activeRouteIdentity: Any? = null
    private var hasObservedRoute = false
    private var recordsRequestId = 0L
    // 현재 계정·모임에 한정한 메모리 캐시. 완료/실패한 주도 새로고침 전에는 다시 요청하지 않는다.
    private val weekRequestIds = mutableMapOf<MissionDate, Long>()
    private val weekRequestJobs = mutableMapOf<MissionDate, Job>()
    private val pendingRecordDates = mutableSetOf<MissionDate>()
    private val recordErrors = mutableMapOf<MissionDate, String>()
    private val recordsRequestLimit = Semaphore(3)
    private var roomRequestId = 0L
    private var roomRequestJob: Job? = null
    private var roomDetailsRefreshId = 0L
    private var roomDetailsRefreshJob: Job? = null
    private var menuActionRequestId = 0L
    private var menuActionJob: Job? = null
    private var menuActionContext: MenuActionContext? = null
    private var consumedMenuActionCompletionId: Long? = null
    private var consumedMenuActionHomeId: Long? = null
    private var isScreenResumed = false
    private val mutableUiState = MutableStateFlow(
        initialState.copy(
            recordsState = initialState.recordsState.copy(
                canViewRecords = canViewRecords,
                records = localRecords[initialState.recordsState.selectedDate]?.records.orEmpty(),
                achievedMemberCount = localRecords[initialState.recordsState.selectedDate]?.achievedMemberCount ?: 0,
                participantCounts = localRecords.mapValues { it.value.achievedMemberCount },
                participantProfiles = localRecords.mapValues { it.value.achievedMembers.map { member -> member.profileImageUrl } },
            ),
            isCalendarVisible = initialState.isCalendarVisible && canViewRecords,
        ).withCurrentSchedule(clock),
    )
    val uiState = mutableUiState.asStateFlow()
    private var countdownJob: Job? = null

    fun onRouteVisible(roomId: String, authState: AuthSessionState, identity: Any?) {
        if (
            hasObservedRoute && activeRoomId == roomId && activeRouteAuthState == authState &&
            activeRouteIdentity === identity
        ) return

        if (hasObservedRoute && activeRoomId != roomId) {
            invalidateMenuAction()
            invalidateRoomDetailsRefresh()
        }
        activeRoomId = roomId
        activeRouteAuthState = authState
        activeRouteIdentity = identity
        hasObservedRoute = true
        onAuthSessionChanged(authState, identity)

        val numericRoomId = roomId.toLongOrNull()?.takeIf { it > 0L }
        if (numericRoomId == null) {
            invalidateRoomRequest()
            showError(RoomHomeInvalidRoomIdMessage, canRetry = false)
            return
        }
        if (authState == AuthSessionState.Restoring) {
            invalidateRoomRequest()
            showLoading()
            return
        }
        if (authState != AuthSessionState.Authenticated || identity == null) {
            invalidateRoomRequest()
            showError(RoomHomeLoginRequiredMessage, canRetry = false)
            return
        }
        if (!isLiveSession(authState, identity)) {
            invalidateRoomRequest()
            showLoading()
            return
        }
        requestRoom(numericRoomId, identity)
    }

    fun onAuthSessionChanged(authState: AuthSessionState, identity: Any?) {
        val changed = observedAuthState != authState || observedAuthIdentity !== identity
        if (!changed) return

        observedAuthState = authState
        observedAuthIdentity = identity
        invalidateRoomRequest()
        invalidateRoomDetailsRefresh()
        invalidateMenuAction()
        clearRoomData()
    }

    fun onRetry() {
        if (!uiState.value.canRetry) return
        val roomId = activeRoomId ?: return
        val numericRoomId = roomId.toLongOrNull()?.takeIf { it > 0L } ?: return
        val identity = activeRouteIdentity ?: return
        if (activeRouteAuthState != AuthSessionState.Authenticated || !isLiveSession(activeRouteAuthState, identity)) return
        requestRoom(numericRoomId, identity)
    }

    /** 메뉴에서 삭제를 고르면 최신 서버 회원 정보부터 확인한다. */
    fun beginDelete() {
        if (uiState.value.membershipRole != RoomMembershipRole.OWNER || uiState.value.menuActionState != null) return
        val context = newMenuActionContext(RoomHomeActionType.Delete) ?: return
        setMenuActionPhase(context, RoomHomeActionPhase.CheckingDeleteEligibility)
        menuActionJob = scope.launch {
            try {
                val details = loadRoom(context.roomId)
                if (!isCurrentMenuAction(context)) return@launch
                val eligibility = deleteEligibility(details, context.roomId)
                when (eligibility) {
                    DeleteEligibility.Allowed -> setMenuActionPhase(context, RoomHomeActionPhase.ConfirmDelete)
                    DeleteEligibility.BlockedByMembers -> setMenuActionPhase(context, RoomHomeActionPhase.DeleteBlockedByMembers)
                    DeleteEligibility.NotOwner -> handleDeletePermissionChanged(context)
                    is DeleteEligibility.Invalid -> setMenuActionError(
                        context,
                        eligibility.message,
                        canRetry = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                handleMenuActionError(context, error)
            }
        }
    }

    /** 참여자는 확인 화면을 먼저 보고, 승인 뒤 최신 참여 상태를 검증한다. */
    fun beginLeave() {
        if (uiState.value.membershipRole != RoomMembershipRole.MEMBER || uiState.value.menuActionState != null) return
        val context = newMenuActionContext(RoomHomeActionType.Leave) ?: return
        setMenuActionPhase(context, RoomHomeActionPhase.ConfirmLeave)
    }

    fun confirmMenuAction() {
        val context = menuActionContext ?: return
        if (!isCurrentMenuAction(context)) {
            invalidateMenuAction()
            return
        }
        when (val state = uiState.value.menuActionState) {
            is RoomHomeMenuActionState.Phase -> when (state.value) {
                RoomHomeActionPhase.ConfirmDelete -> confirmDelete(context)
                RoomHomeActionPhase.ConfirmLeave -> confirmLeave(context)
                else -> Unit
            }
            else -> Unit
        }
    }

    fun cancelMenuAction() {
        val state = uiState.value.menuActionState ?: return
        if (state.isProtected()) return
        invalidateMenuAction()
    }

    /** 재시도는 매번 최신 상세를 다시 읽고, 삭제 요청은 다시 사용자 승인을 받는다. */
    fun retryMenuAction() {
        val current = uiState.value.menuActionState as? RoomHomeMenuActionState.Error ?: return
        if (!current.canRetry) return
        val oldContext = menuActionContext ?: return
        if (!isCurrentMenuAction(oldContext)) {
            invalidateMenuAction()
            return
        }
        when (oldContext.actionType) {
            RoomHomeActionType.Delete -> beginDeleteRetry(oldContext)
            RoomHomeActionType.Leave -> retryLeaveAfterCheck(oldContext)
        }
    }

    fun consumeMenuActionCompletion(operationId: Long): RoomHomeMenuActionCompletion? {
        val state = uiState.value.menuActionState as? RoomHomeMenuActionState.Completed ?: return null
        val context = menuActionContext ?: return null
        if (
            state.operationId != operationId || context.operationId != operationId ||
            consumedMenuActionCompletionId == operationId || !isCurrentMenuAction(context)
        ) return null
        consumedMenuActionCompletionId = operationId
        return context.toCompletion()
    }

    fun consumeMenuActionHomeNavigation(operationId: Long): RoomHomeMenuActionCompletion? {
        val state = uiState.value.menuActionState ?: return null
        val context = menuActionContext ?: return null
        if (
            state.operationId != operationId || context.operationId != operationId ||
            consumedMenuActionHomeId == operationId || !isCurrentMenuAction(context)
        ) return null
        consumedMenuActionHomeId = operationId
        return context.toCompletion()
    }

    /** 회원 관리에서 돌아오면 원래 화면 데이터를 유지한 채 서버 상세만 갱신한다. */
    fun refreshRoomDetails() {
        val roomId = activeRoomId?.toLongOrNull()?.takeIf { it > 0L } ?: return
        val identity = activeRouteIdentity ?: return
        if (activeRouteAuthState != AuthSessionState.Authenticated || !isLiveSession(activeRouteAuthState, identity)) return
        invalidateRoomDetailsRefresh()
        val requestId = roomDetailsRefreshId
        roomDetailsRefreshJob = scope.launch {
            try {
                val details = loadRoom(roomId)
                if (!isCurrentRoomDetailsRefresh(requestId, roomId, identity)) return@launch
                if (details.room.id != roomId || !details.hasReliableMemberList()) return@launch
                applyRefreshedRoomDetails(details)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                // 화면 복귀 시의 보조 갱신이 실패해도 이미 표시한 상세 상태는 보존한다.
            }
        }
    }

    private fun beginDeleteRetry(previous: MenuActionContext) {
        if (!isCurrentMenuAction(previous)) return
        setMenuActionPhase(previous, RoomHomeActionPhase.CheckingDeleteEligibility)
        menuActionJob = scope.launch {
            try {
                val details = loadRoom(previous.roomId)
                if (!isCurrentMenuAction(previous)) return@launch
                val eligibility = deleteEligibility(details, previous.roomId)
                when (eligibility) {
                    DeleteEligibility.Allowed -> setMenuActionPhase(previous, RoomHomeActionPhase.ConfirmDelete)
                    DeleteEligibility.BlockedByMembers -> setMenuActionPhase(previous, RoomHomeActionPhase.DeleteBlockedByMembers)
                    DeleteEligibility.NotOwner -> handleDeletePermissionChanged(previous)
                    is DeleteEligibility.Invalid -> setMenuActionError(
                        previous,
                        eligibility.message,
                        canRetry = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                handleMenuActionError(previous, error)
            }
        }
    }

    private fun confirmDelete(context: MenuActionContext) {
        setMenuActionPhase(context, RoomHomeActionPhase.RecheckingBeforeDelete)
        menuActionJob = scope.launch {
            try {
                val details = loadRoom(context.roomId)
                if (!isCurrentMenuAction(context)) return@launch
                val eligibility = deleteEligibility(details, context.roomId)
                when (eligibility) {
                    DeleteEligibility.Allowed -> {
                        setMenuActionPhase(context, RoomHomeActionPhase.Deleting)
                        deleteRoom(context.roomId)
                        if (!isCurrentMenuAction(context)) return@launch
                        invalidateRoomRequest()
                        invalidateRoomDetailsRefresh()
                        setMenuActionCompleted(context)
                    }
                    DeleteEligibility.BlockedByMembers -> setMenuActionPhase(
                        context,
                        RoomHomeActionPhase.DeleteBlockedByMembers,
                    )
                    DeleteEligibility.NotOwner -> handleDeletePermissionChanged(context)
                    is DeleteEligibility.Invalid -> setMenuActionError(
                        context,
                        eligibility.message,
                        canRetry = true,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                handleMenuActionError(context, error)
            }
        }
    }

    private fun confirmLeave(context: MenuActionContext) {
        setMenuActionPhase(context, RoomHomeActionPhase.CheckingLeaveEligibility)
        menuActionJob = scope.launch {
            try {
                val details = loadRoom(context.roomId)
                if (!isCurrentMenuAction(context)) return@launch
                if (
                    details.room.id != context.roomId || details.membershipRole != RoomMembershipRole.MEMBER ||
                    !details.room.isJoined
                ) {
                    setMenuActionError(
                        context,
                        "모임 참여 상태가 변경되었어요. 모임 목록을 새로 확인해 주세요.",
                        isMembershipChanged = true,
                        canRetry = false,
                    )
                    refreshRoomDetails()
                    return@launch
                }
                setMenuActionPhase(context, RoomHomeActionPhase.Leaving)
                leaveRoom(context.roomId)
                if (!isCurrentMenuAction(context)) return@launch
                invalidateRoomRequest()
                invalidateRoomDetailsRefresh()
                setMenuActionCompleted(context)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                handleMenuActionError(context, error)
            }
        }
    }

    private fun retryLeaveAfterCheck(context: MenuActionContext) {
        setMenuActionPhase(context, RoomHomeActionPhase.CheckingLeaveEligibility)
        menuActionJob = scope.launch {
            try {
                val details = loadRoom(context.roomId)
                if (!isCurrentMenuAction(context)) return@launch
                if (
                    details.room.id != context.roomId || details.membershipRole != RoomMembershipRole.MEMBER ||
                    !details.room.isJoined
                ) {
                    setMenuActionError(
                        context,
                        "모임 참여 상태가 변경되었어요. 모임 목록을 새로 확인해 주세요.",
                        isMembershipChanged = true,
                        canRetry = false,
                    )
                    refreshRoomDetails()
                } else {
                    setMenuActionPhase(context, RoomHomeActionPhase.ConfirmLeave)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                handleMenuActionError(context, error)
            }
        }
    }

    private fun newMenuActionContext(actionType: RoomHomeActionType): MenuActionContext? {
        val roomId = activeRoomId?.toLongOrNull()?.takeIf { it > 0L } ?: return null
        val identity = activeRouteIdentity ?: return null
        if (activeRouteAuthState != AuthSessionState.Authenticated || !isLiveSession(activeRouteAuthState, identity)) return null
        invalidateMenuAction()
        val context = MenuActionContext(++menuActionRequestId, roomId, identity, actionType)
        menuActionContext = context
        return context
    }

    private fun deleteEligibility(details: RoomMembershipDetails, roomId: Long): DeleteEligibility {
        if (details.room.id != roomId) return DeleteEligibility.Invalid("조회한 모임 정보가 요청한 모임과 달라요. 다시 시도해 주세요.")
        if (details.membershipRole != RoomMembershipRole.OWNER || !details.room.isJoined) return DeleteEligibility.NotOwner
        if (!details.hasReliableMemberList()) return DeleteEligibility.Invalid("회원 정보를 확인할 수 없어 모임을 삭제하지 않았어요. 다시 확인해 주세요.")
        return if (details.room.memberCount == 1) DeleteEligibility.Allowed else DeleteEligibility.BlockedByMembers
    }

    private fun RoomMembershipDetails.hasReliableMemberList(): Boolean =
        room.memberCount > 0 && members.size == room.memberCount &&
            members.all { it.userId > 0L } && members.map { it.userId }.distinct().size == members.size

    private fun applyRefreshedRoomDetails(details: RoomMembershipDetails) {
        val room = details.room.toRoomUiModel()
        val members = details.members.map { member ->
            RoomHomeMemberUiModel(
                id = member.userId.toString(),
                nickname = member.nickname,
                profileImageUrl = member.profileImageUrl,
            )
        }
        if (!room.isJoined) {
            canViewRecords = false
            invalidateRecordsRequest()
            localRecords = emptyMap()
        }
        mutableUiState.update { state ->
            state.copy(
                room = room,
                membershipRole = details.membershipRole,
                members = members,
                areMembersLoaded = true,
                recordsState = if (room.isJoined) state.recordsState else state.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
                    participantProfiles = emptyMap(),
                    isLoading = false,
                    errorMessage = null,
                    canViewRecords = false,
                    isDataLoaded = false,
                ),
                isCalendarVisible = state.isCalendarVisible && room.isJoined,
            ).withCurrentSchedule(clock)
        }
    }

    private fun handleMenuActionError(context: MenuActionContext, error: Throwable) {
        if (!isCurrentMenuAction(context)) return
        val apiError = error as? RoomRepositoryException
        when {
            apiError?.statusCode == 404 || apiError?.code in setOf("COMMON404", "ROOM404") ||
                (context.actionType == RoomHomeActionType.Leave &&
                    (apiError?.statusCode == 409 || apiError?.code == "ROOM409")) -> {
                setMenuActionError(
                    context,
                    "모임이 삭제되었거나 참여 상태가 변경되었어요. 모임 목록을 새로 확인해 주세요.",
                    isMembershipChanged = true,
                    canRetry = false,
                )
                refreshRoomDetails()
            }
            apiError?.statusCode == 403 || apiError?.code in setOf("COMMON403", "ROOM403") -> {
                setMenuActionError(
                    context,
                    "요청을 처리할 권한이 없어요. 모임 정보를 다시 확인해 주세요.",
                    canRetry = false,
                )
                refreshRoomDetails()
            }
            else -> setMenuActionError(
                context,
                "${if (context.actionType == RoomHomeActionType.Delete) "모임을 삭제" else "모임에서 탈퇴"}하지 못했어요. 상태를 확인하고 다시 시도해 주세요.",
                canRetry = true,
            )
        }
    }

    private fun handleDeletePermissionChanged(context: MenuActionContext) {
        setMenuActionError(
            context,
            "모임을 삭제할 수 있는 방장 권한이 없어요. 모임 정보를 새로 확인해 주세요.",
            canRetry = false,
        )
        refreshRoomDetails()
    }

    private fun setMenuActionPhase(context: MenuActionContext, phase: RoomHomeActionPhase) {
        if (!isCurrentMenuAction(context)) return
        mutableUiState.update {
            it.copy(menuActionState = RoomHomeMenuActionState.Phase(context.operationId, context.actionType, phase))
        }
    }

    private fun setMenuActionError(
        context: MenuActionContext,
        message: String,
        isMembershipChanged: Boolean = false,
        canRetry: Boolean = true,
    ) {
        if (!isCurrentMenuAction(context)) return
        mutableUiState.update {
            it.copy(
                menuActionState = RoomHomeMenuActionState.Error(
                    operationId = context.operationId,
                    actionType = context.actionType,
                    message = message,
                    isMembershipChanged = isMembershipChanged,
                    canRetry = canRetry,
                ),
            )
        }
    }

    private fun setMenuActionCompleted(context: MenuActionContext) {
        if (!isCurrentMenuAction(context)) return
        mutableUiState.update {
            it.copy(menuActionState = RoomHomeMenuActionState.Completed(context.operationId, context.actionType))
        }
    }

    private fun isCurrentMenuAction(context: MenuActionContext): Boolean =
        menuActionContext == context && menuActionRequestId == context.operationId &&
            activeRoomId?.toLongOrNull() == context.roomId && activeRouteAuthState == AuthSessionState.Authenticated &&
            activeRouteIdentity === context.sessionIdentity && isLiveSession(AuthSessionState.Authenticated, context.sessionIdentity)

    private fun invalidateMenuAction() {
        menuActionRequestId += 1L
        menuActionJob?.cancel()
        menuActionJob = null
        menuActionContext = null
        consumedMenuActionCompletionId = null
        consumedMenuActionHomeId = null
        mutableUiState.update { it.copy(menuActionState = null) }
    }

    private fun invalidateRoomDetailsRefresh() {
        roomDetailsRefreshId += 1L
        roomDetailsRefreshJob?.cancel()
        roomDetailsRefreshJob = null
    }

    private fun isCurrentRoomDetailsRefresh(requestId: Long, roomId: Long, identity: Any?): Boolean =
        roomDetailsRefreshId == requestId && activeRoomId?.toLongOrNull() == roomId &&
            activeRouteAuthState == AuthSessionState.Authenticated && activeRouteIdentity === identity &&
            isLiveSession(AuthSessionState.Authenticated, identity)

    private fun MenuActionContext.toCompletion() = RoomHomeMenuActionCompletion(
        operationId = operationId,
        actionType = actionType,
        roomId = roomId,
        sessionIdentity = sessionIdentity,
    )

    private fun RoomHomeMenuActionState.isProtected(): Boolean = blocksNavigationBack

    private data class MenuActionContext(
        val operationId: Long,
        val roomId: Long,
        val sessionIdentity: Any,
        val actionType: RoomHomeActionType,
    )

    private sealed interface DeleteEligibility {
        data object Allowed : DeleteEligibility
        data object BlockedByMembers : DeleteEligibility
        data object NotOwner : DeleteEligibility
        data class Invalid(val message: String) : DeleteEligibility
    }

    /** 화면 복귀와 상세 응답 이후 모두 실제 현재 시각으로 일정을 계산한다. */
    fun startCountdown() {
        isScreenResumed = true
        mutableUiState.update { it.withCurrentSchedule(clock) }
        ensureCountdownJob()
    }

    fun stopCountdown() {
        isScreenResumed = false
        cancelCountdownJob()
    }

    private fun cancelCountdownJob() {
        countdownJob?.cancel()
        countdownJob = null
    }

    private fun requestRoom(roomId: Long, identity: Any?) {
        invalidateRoomRequest()
        val requestId = roomRequestId
        showLoading()
        roomRequestJob = scope.launch {
            try {
                val details = loadRoom(roomId)
                check(details.room.id == roomId) { "조회한 모임 ID가 요청과 달라요." }
                val room = details.room.toRoomUiModel()
                val members = details.members.map { member ->
                    RoomHomeMemberUiModel(
                        id = member.userId.toString(),
                        nickname = member.nickname,
                        profileImageUrl = member.profileImageUrl,
                    )
                }
                if (!isCurrentRequest(requestId, roomId, identity)) return@launch

                localRecords = emptyMap()
                canViewRecords = room.isJoined
                mutableUiState.update { state ->
                    state.copy(
                        room = room,
                        membershipRole = details.membershipRole,
                        members = members,
                        areMembersLoaded = true,
                        isLoading = false,
                        errorMessage = null,
                        canRetry = false,
                        recordsState = state.recordsState.copy(
                            records = emptyList(),
                            achievedMemberCount = 0,
                            participantCounts = emptyMap(),
                            participantProfiles = emptyMap(),
                            isLoading = false,
                            errorMessage = null,
                            canViewRecords = canViewRecords,
                            isDataLoaded = false,
                        ),
                        isCalendarVisible = false,
                        ongoingActivity = null,
                    ).withCurrentSchedule(clock)
                }
                ensureCountdownJob()
                if (uiState.value.selectedTab == RoomHomeTab.Records) requestRecords()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentRequest(requestId, roomId, identity)) return@launch
                val (message, canRetry) = roomLoadError(error)
                showError(message, canRetry)
            }
        }
    }

    private fun isCurrentRequest(requestId: Long, roomId: Long, identity: Any?): Boolean =
        roomRequestId == requestId &&
            activeRoomId?.toLongOrNull() == roomId &&
            activeRouteAuthState == AuthSessionState.Authenticated &&
            activeRouteIdentity === identity &&
            isLiveSession(AuthSessionState.Authenticated, identity)

    private fun isLiveSession(authState: AuthSessionState?, identity: Any?): Boolean =
        authState == AuthSessionState.Authenticated &&
            identity != null &&
            observedAuthState == authState &&
            observedAuthIdentity === identity &&
            authSession.state.value == authState &&
            authSession.identity.value === identity

    private fun invalidateRoomRequest() {
        invalidateRecordsRequest()
        roomRequestId += 1L
        roomRequestJob?.cancel()
        roomRequestJob = null
    }

    private fun clearRoomData() {
        canViewRecords = false
        localRecords = emptyMap()
        cancelCountdownJob()
        mutableUiState.update { state ->
            state.copy(
                room = null,
                membershipRole = null,
                members = emptyList(),
                areMembersLoaded = false,
                isLoading = false,
                errorMessage = null,
                canRetry = false,
                recordsState = state.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
                    participantProfiles = emptyMap(),
                    isLoading = false,
                    errorMessage = null,
                    canViewRecords = false,
                    isDataLoaded = false,
                ),
                isCalendarVisible = false,
                nextScheduleText = null,
                remainingTimeText = null,
                ongoingActivity = null,
            )
        }
    }

    private fun showLoading() {
        canViewRecords = false
        localRecords = emptyMap()
        cancelCountdownJob()
        mutableUiState.update { state ->
            state.copy(
                room = null,
                membershipRole = null,
                members = emptyList(),
                areMembersLoaded = false,
                isLoading = true,
                errorMessage = null,
                canRetry = false,
                recordsState = state.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
                    participantProfiles = emptyMap(),
                    isLoading = false,
                    errorMessage = null,
                    canViewRecords = false,
                    isDataLoaded = false,
                ),
                isCalendarVisible = false,
                nextScheduleText = null,
                remainingTimeText = null,
                ongoingActivity = null,
            )
        }
    }

    private fun showError(message: String, canRetry: Boolean) {
        canViewRecords = false
        localRecords = emptyMap()
        cancelCountdownJob()
        mutableUiState.update { state ->
            state.copy(
                room = null,
                membershipRole = null,
                members = emptyList(),
                areMembersLoaded = false,
                isLoading = false,
                errorMessage = message,
                canRetry = canRetry,
                recordsState = state.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
                    participantProfiles = emptyMap(),
                    isLoading = false,
                    errorMessage = null,
                    canViewRecords = false,
                    isDataLoaded = false,
                ),
                isCalendarVisible = false,
                nextScheduleText = null,
                remainingTimeText = null,
                ongoingActivity = null,
            )
        }
    }

    private fun ensureCountdownJob() {
        if (
            !isScreenResumed || countdownJob?.isActive == true ||
            uiState.value.room?.toActivitySchedule()?.days.isNullOrEmpty()
        ) return
        countdownJob = scope.launch {
            while (isActive) {
                delay(1_000)
                mutableUiState.update { it.withCurrentSchedule(clock) }
            }
        }
    }

    override fun onCleared() {
        invalidateRoomRequest()
        invalidateRoomDetailsRefresh()
        invalidateMenuAction()
        stopCountdown()
        super.onCleared()
    }

    fun onTabSelected(tab: RoomHomeTab) {
        mutableUiState.update { it.copy(selectedTab = tab, isCalendarVisible = false) }
        if (tab == RoomHomeTab.Records) requestRecords()
    }

    fun onDateSelected(date: MissionDate) {
        val changed = uiState.value.recordsState.selectedDate != date
        if (!changed && activeRoomId != null) {
            mutableUiState.update { it.copy(isCalendarVisible = false) }
            return
        }
        mutableUiState.update { state ->
            val day = localRecords[date] ?: RoomHomeDayRecordsUiModel()
            state.copy(
                recordsState = state.recordsState.copy(
                    selectedDate = date,
                    visibleWeekStart = date.weekDates().first(),
                    records = day.records,
                    achievedMemberCount = day.achievedMemberCount,
                    participantCounts = localRecords.mapValues { it.value.achievedMemberCount },
                    participantProfiles = localRecords.mapValues { it.value.achievedMembers.map { member -> member.profileImageUrl } },
                    isLoading = false,
                    errorMessage = null,
                ),
                calendarMonth = date.yearMonth,
                isCalendarVisible = false,
            )
        }
        if (activeRoomId != null) showSelectedDayRecords()
        if (changed && uiState.value.selectedTab == RoomHomeTab.Records) requestRecords()
    }

    fun onPreviousWeek() = onDateSelected(uiState.value.recordsState.selectedDate.plusDays(-7))

    fun onNextWeek() = onDateSelected(uiState.value.recordsState.selectedDate.plusDays(7))

    fun onOpenCalendar() {
        mutableUiState.update {
            it.copy(isCalendarVisible = canViewRecords, calendarMonth = it.recordsState.selectedDate.yearMonth)
        }
    }

    fun onDismissCalendar() {
        mutableUiState.update { it.copy(isCalendarVisible = false) }
    }

    fun onPreviousMonth() {
        mutableUiState.update { it.copy(calendarMonth = it.calendarMonth.previous()) }
    }

    fun onNextMonth() {
        mutableUiState.update { it.copy(calendarMonth = it.calendarMonth.next()) }
    }

    fun onRefresh() {
        if (activeRoomId == null) onDateSelected(uiState.value.recordsState.selectedDate)
        else requestRecords(refresh = true)
    }

    private fun invalidateRecordsRequest() {
        recordsRequestId += 1
        // 먼저 토큰을 제거해 취소를 무시하고 도착한 응답도 캐시를 복원하지 못하게 한다.
        weekRequestIds.clear()
        val jobs = weekRequestJobs.values.toList()
        weekRequestJobs.clear()
        pendingRecordDates.clear()
        recordErrors.clear()
        jobs.forEach(Job::cancel)
    }

    private fun requestRecords(refresh: Boolean = false) {
        val roomId = activeRoomId?.toLongOrNull() ?: return
        val identity = activeRouteIdentity ?: return
        if (!canViewRecords || !isLiveSession(activeRouteAuthState, identity)) return
        val selectedDate = uiState.value.recordsState.selectedDate
        val dates = selectedDate.weekDates()
        val weekStart = dates.first()
        if (!refresh && weekStart in weekRequestIds) {
            showSelectedDayRecords()
            return
        }

        val requestId = ++recordsRequestId
        weekRequestIds[weekStart] = requestId
        weekRequestJobs.remove(weekStart)?.cancel()
        localRecords = localRecords - dates.toSet()
        dates.forEach(recordErrors::remove)
        pendingRecordDates.addAll(dates)
        showSelectedDayRecords()

        // API가 하루 단위이므로 일주일치 요청을 묶되, 선택 날짜부터 최대 3개씩 조회한다.
        // 다른 주로 이동해도 진행 중인 조회는 완료해 캐시에 저장한다.
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                coroutineScope {
                    (listOf(selectedDate) + dates.filter { it != selectedDate }).forEach { date ->
                        launch {
                            recordsRequestLimit.withPermit {
                                if (!isCurrentRecordsRequest(requestId, roomId, weekStart, identity)) return@withPermit
                                try {
                                    val day = loadRecords(roomId, date).toDayUiModel()
                                    if (!isCurrentRecordsRequest(requestId, roomId, weekStart, identity)) return@withPermit
                                    localRecords = localRecords + (date to day)
                                } catch (error: CancellationException) {
                                    throw error
                                } catch (error: Throwable) {
                                    if (!isCurrentRecordsRequest(requestId, roomId, weekStart, identity)) return@withPermit
                                    val (message, canRetry) = roomLoadError(error)
                                    if (!canRetry) {
                                        invalidateRoomRequest()
                                        showError(message, canRetry = false)
                                    } else {
                                        // 실패를 빈 기록으로 캐시하지 않는다. 해당 날짜를 고르면 재시도 안내를 표시한다.
                                        recordErrors[date] = "기록을 불러오지 못했어요. 새로고침으로 다시 시도해 주세요."
                                    }
                                } finally {
                                    if (isCurrentRecordsRequest(requestId, roomId, weekStart, identity)) {
                                        pendingRecordDates.remove(date)
                                        showSelectedDayRecords()
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                if (weekRequestIds[weekStart] == requestId) weekRequestJobs.remove(weekStart)
            }
        }
        weekRequestJobs[weekStart] = job
        job.start()
    }

    private fun showSelectedDayRecords() {
        mutableUiState.update { state ->
            val date = state.recordsState.selectedDate
            val day = localRecords[date]
            state.copy(recordsState = state.recordsState.copy(
                records = day?.records.orEmpty(),
                achievedMemberCount = day?.achievedMemberCount ?: 0,
                participantCounts = localRecords.mapValues { it.value.achievedMemberCount },
                participantProfiles = localRecords.mapValues { it.value.achievedMembers.map { member -> member.profileImageUrl } },
                isLoading = date in pendingRecordDates,
                isDataLoaded = day != null,
                errorMessage = recordErrors[date],
            ))
        }
    }

    private fun isCurrentRecordsRequest(requestId: Long, roomId: Long, weekStart: MissionDate, identity: Any): Boolean =
        weekRequestIds[weekStart] == requestId && activeRoomId?.toLongOrNull() == roomId &&
            activeRouteIdentity === identity && isLiveSession(activeRouteAuthState, identity)

}

private fun roomLoadError(error: Throwable): Pair<String, Boolean> {
    val apiError = error as? RoomRepositoryException
    return when {
        apiError?.statusCode == 401 || apiError?.code in setOf("AUTH401", "COMMON401", "ROOM401") ->
            RoomHomeLoginRequiredMessage to false

        apiError?.statusCode == 403 || apiError?.code in setOf("COMMON403", "ROOM403", "RECORD403") ->
            RoomHomeForbiddenMessage to false

        apiError?.statusCode == 404 || apiError?.code in setOf("COMMON404", "ROOM404") ->
            RoomHomeNotFoundMessage to false

        else -> RoomHomeLoadFailedMessage to true
    }
}

private const val RoomHomeInvalidRoomIdMessage = "모임 정보가 올바르지 않아요. 모임 목록으로 돌아가 주세요."
private const val RoomHomeLoginRequiredMessage = "로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요."
private const val RoomHomeForbiddenMessage = "이 모임에 참여하고 있지 않아요. 모임 목록으로 돌아가 주세요."
private const val RoomHomeNotFoundMessage = "모임이 존재하지 않거나 삭제됐어요. 모임 목록으로 돌아가 주세요."
private const val RoomHomeLoadFailedMessage = "모임 정보를 불러오지 못했어요. 다시 시도해 주세요."
