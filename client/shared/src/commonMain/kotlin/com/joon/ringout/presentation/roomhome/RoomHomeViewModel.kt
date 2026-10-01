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
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomScheduleClock
import com.joon.ringout.presentation.roomlist.model.toRoomUiModel
import kotlinx.coroutines.CancellationException
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
    private var roomRequestId = 0L
    private var roomRequestJob: Job? = null
    private var isScreenResumed = false
    private val mutableUiState = MutableStateFlow(
        initialState.copy(
            recordsState = initialState.recordsState.copy(
                canViewRecords = canViewRecords,
                records = localRecords[initialState.recordsState.selectedDate]?.records.orEmpty(),
                achievedMemberCount = localRecords[initialState.recordsState.selectedDate]?.achievedMemberCount ?: 0,
                participantCounts = localRecords.mapValues { it.value.achievedMemberCount },
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
                        members = members,
                        areMembersLoaded = true,
                        isLoading = false,
                        errorMessage = null,
                        canRetry = false,
                        recordsState = state.recordsState.copy(
                            records = emptyList(),
                            achievedMemberCount = 0,
                            participantCounts = emptyMap(),
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
                members = emptyList(),
                areMembersLoaded = false,
                isLoading = false,
                errorMessage = null,
                canRetry = false,
                recordsState = state.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
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
                members = emptyList(),
                areMembersLoaded = false,
                isLoading = true,
                errorMessage = null,
                canRetry = false,
                recordsState = state.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
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
                members = emptyList(),
                areMembersLoaded = false,
                isLoading = false,
                errorMessage = message,
                canRetry = canRetry,
                recordsState = state.recordsState.copy(
                    records = emptyList(),
                    achievedMemberCount = 0,
                    participantCounts = emptyMap(),
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
        stopCountdown()
        super.onCleared()
    }

    fun onTabSelected(tab: RoomHomeTab) {
        mutableUiState.update { it.copy(selectedTab = tab, isCalendarVisible = false) }
    }

    fun onDateSelected(date: MissionDate) {
        mutableUiState.update { state ->
            val day = localRecords[date] ?: RoomHomeDayRecordsUiModel()
            state.copy(
                recordsState = state.recordsState.copy(
                    selectedDate = date,
                    visibleWeekStart = date.weekDates().first(),
                    records = day.records,
                    achievedMemberCount = day.achievedMemberCount,
                    participantCounts = localRecords.mapValues { it.value.achievedMemberCount },
                    isLoading = false,
                    errorMessage = null,
                ),
                calendarMonth = date.yearMonth,
                isCalendarVisible = false,
            )
        }
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

    /** 현재 선택 날짜의 메모리 데이터를 다시 표시한다. 네트워크 새로고침은 후속 API 작업에서 연결한다. */
    fun onRefresh() = onDateSelected(uiState.value.recordsState.selectedDate)
}

private fun roomLoadError(error: Throwable): Pair<String, Boolean> {
    val apiError = error as? RoomRepositoryException
    return when {
        apiError?.statusCode == 401 || apiError?.code in setOf("AUTH401", "COMMON401", "ROOM401") ->
            RoomHomeLoginRequiredMessage to false

        apiError?.statusCode == 403 || apiError?.code in setOf("COMMON403", "ROOM403") ->
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
