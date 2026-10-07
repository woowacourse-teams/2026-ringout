package com.joon.ringout.presentation.roomactivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomMemberMovement
import com.joon.ringout.domain.room.RoomRecordEvent
import com.joon.ringout.domain.room.RoomRecords
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.roomRecordsDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class RoomActivityViewModel(
    private val loadMembers: suspend (Long) -> List<RoomMemberMovement> = {
        throw IllegalStateException("회원 이동 상태 조회를 사용할 수 없어요.")
    },
    private val loadRecords: suspend (Long, MissionDate) -> RoomRecords = { _, _ ->
        throw IllegalStateException("모임 활동 기록 조회를 사용할 수 없어요.")
    },
    private val currentRecordsDate: () -> MissionDate = { roomRecordsDate() },
    private val authSession: AuthSession = AuthSession(),
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private val mutableUiState = MutableStateFlow(RoomActivityUiState())
    val uiState = mutableUiState.asStateFlow()

    private var activeRoomId: String? = null
    private var activeActivityDate: MissionDate? = null
    private var observedAuthState = authSession.state.value
    private var observedAuthIdentity = authSession.identity.value
    private var requestGeneration = 0L
    private var membersJob: Job? = null
    private var recordsJob: Job? = null
    private var pollingJob: Job? = null
    private var isResumed = false
    private var isMemberAutoPollingStopped = false
    private var isTimelineAutoPollingStopped = false

    fun onRouteVisible(
        roomId: String,
        activityDate: String,
        authState: AuthSessionState = authSession.state.value,
        identity: Any? = authSession.identity.value,
    ) {
        val parsedActivityDate = activityDate.toMissionDateOrNull()
        val sameRouteAndSession = activeRoomId == roomId &&
            activeActivityDate == parsedActivityDate &&
            observedAuthState == authState &&
            observedAuthIdentity === identity

        activeRoomId = roomId
        activeActivityDate = parsedActivityDate
        observedAuthState = authState
        observedAuthIdentity = identity

        if (!sameRouteAndSession) {
            invalidateRequests()
            stopPolling()
            resetAutoPollingStops()
            mutableUiState.value = RoomActivityUiState(roomId = roomId, activityDate = parsedActivityDate)
        }

        if (parsedActivityDate == null || roomId.toLongOrNull()?.takeIf { it > 0L } == null) {
            showActivityError("모임 활동 정보가 올바르지 않아요.", canRetry = false)
            return
        }

        if (isResumed) {
            requestBoth()
            startPolling()
        }
    }

    fun onAuthSessionChanged(authState: AuthSessionState, identity: Any?) {
        if (authState == observedAuthState && identity === observedAuthIdentity) return
        observedAuthState = authState
        observedAuthIdentity = identity
        invalidateRequests()
        stopPolling()
        resetAutoPollingStops()
        mutableUiState.value = RoomActivityUiState(
            roomId = activeRoomId.orEmpty(),
            activityDate = activeActivityDate,
        )
    }

    fun onResume() {
        val wasResumed = isResumed
        isResumed = true
        if (!wasResumed && hasValidRoute()) {
            resetAutoPollingStops()
            requestBoth()
        }
        startPolling()
    }

    fun onPause() {
        isResumed = false
        stopPolling()
        invalidateRequests()
    }

    fun onRefresh() {
        resetAutoPollingStops()
        invalidateRequests()
        requestBoth()
        startPolling()
    }

    fun onRetry() = onRefresh()

    fun onRetryMembers() {
        isMemberAutoPollingStopped = false
        requestMembers()
        startPolling()
    }

    fun onRetryTimeline() {
        isTimelineAutoPollingStopped = false
        requestRecords()
        startPolling()
    }

    fun onMembersClick(memberIds: List<String>) {
        mutableUiState.update { state ->
            state.copy(selectedMemberIds = memberIds.distinct().filter { id -> state.members.any { it.id == id } })
        }
    }

    fun onCloseMembers() {
        mutableUiState.update { it.copy(selectedMemberIds = null) }
    }

    private fun requestBoth() {
        requestMembers()
        requestRecords()
    }

    private fun requestMembers() {
        if (!isResumed || membersJob?.isActive == true) return
        val context = requestContext() ?: return
        val preserveMembers = mutableUiState.value.isDataLoaded
        mutableUiState.update { state ->
            state.copy(
                roomId = context.roomKey,
                activityDate = context.activityDate,
                isInitialLoading = !preserveMembers,
                isRefreshing = preserveMembers,
                errorMessage = null,
                refreshErrorMessage = null,
                canRetry = false,
            )
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext()[Job]
            try {
                val movements = loadMembers(context.roomId)
                if (!isCurrentRequest(context)) return@launch

                val members = movements.map(RoomMemberMovement::toRoomActivityMemberUiModel)
                mutableUiState.update { state ->
                    val selected = state.selectedMemberIds
                        ?.filter { id -> members.any { it.id == id } }
                        ?.takeIf { it.isNotEmpty() }
                    state.copy(
                        members = members,
                        selectedMemberIds = selected,
                        isInitialLoading = false,
                        isRefreshing = false,
                        isDataLoaded = true,
                        errorMessage = null,
                        refreshErrorMessage = null,
                        canRetry = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentRequest(context)) return@launch
                val repositoryError = error as? RoomRepositoryException
                if (repositoryError?.isPermanentActivityFailure() == true) {
                    showAccessFailure(repositoryError)
                } else {
                    handleMemberFailure(preserveMembers)
                }
            } finally {
                if (membersJob === ownJob) membersJob = null
            }
        }
        membersJob = job
        job.start()
    }

    private fun requestRecords() {
        if (!isResumed || recordsJob?.isActive == true) return
        val context = requestContext() ?: return
        val hasRecords = mutableUiState.value.isTimelineDataLoaded
        mutableUiState.update { state ->
            state.copy(
                isTimelineInitialLoading = !hasRecords,
                isTimelineRefreshing = hasRecords,
                timelineErrorMessage = null,
                timelineRefreshErrorMessage = null,
                canRetryTimeline = false,
                showTimeline = true,
            )
        }

        val job = scope.launch(start = CoroutineStart.LAZY) {
            val ownJob = currentCoroutineContext()[Job]
            var requestedDate: MissionDate? = null
            var requestTodayAgain = false
            try {
                val date = currentRecordsDate()
                requestedDate = date
                if (!isCurrentRequest(context)) return@launch
                updateTimelineDate(date)

                val records = loadRecords(context.roomId, date)
                if (!isCurrentRequest(context)) return@launch

                val currentDate = currentRecordsDate()
                if (currentDate != date) {
                    updateTimelineDate(currentDate)
                    requestTodayAgain = true
                    return@launch
                }

                mutableUiState.update { state ->
                    state.copy(
                        timeline = records.toRoomActivityTimelineUiModels(),
                        timelineDate = date,
                        showTimeline = true,
                        isTimelineInitialLoading = false,
                        isTimelineRefreshing = false,
                        isTimelineDataLoaded = true,
                        timelineErrorMessage = null,
                        timelineRefreshErrorMessage = null,
                        canRetryTimeline = false,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentRequest(context)) return@launch

                val date = requestedDate
                if (date != null) {
                    val currentDate = currentRecordsDate()
                    if (currentDate != date) {
                        updateTimelineDate(currentDate)
                        requestTodayAgain = true
                        return@launch
                    }
                }

                val repositoryError = error as? RoomRepositoryException
                if (repositoryError?.isPermanentActivityFailure() == true) {
                    showAccessFailure(repositoryError)
                } else {
                    handleTimelineFailure(date)
                }
            } finally {
                if (recordsJob === ownJob) recordsJob = null
                if (
                    requestTodayAgain && isResumed && requestGeneration == context.generation &&
                    isLiveSession(observedAuthState, observedAuthIdentity)
                ) {
                    requestRecords()
                }
            }
        }
        recordsJob = job
        job.start()
    }

    private fun updateTimelineDate(date: MissionDate) {
        mutableUiState.update { state ->
            if (state.timelineDate == date) {
                state.copy(showTimeline = true)
            } else {
                state.copy(
                    timeline = emptyList(),
                    timelineDate = date,
                    selectedMemberIds = null,
                    showTimeline = true,
                    isTimelineInitialLoading = true,
                    isTimelineRefreshing = false,
                    isTimelineDataLoaded = false,
                    timelineErrorMessage = null,
                    timelineRefreshErrorMessage = null,
                    canRetryTimeline = false,
                )
            }
        }
    }

    private fun requestContext(): ActivityRequestContext? {
        val roomKey = activeRoomId.orEmpty()
        val activityDate = activeActivityDate
        val numericRoomId = roomKey.toLongOrNull()?.takeIf { it > 0L }
        if (activityDate == null || numericRoomId == null) {
            showActivityError("모임 활동 정보가 올바르지 않아요.", canRetry = false)
            return null
        }

        when (observedAuthState) {
            AuthSessionState.Restoring -> {
                mutableUiState.update { state ->
                    state.copy(
                        isInitialLoading = !state.isDataLoaded,
                        isRefreshing = state.isDataLoaded,
                        isTimelineInitialLoading = !state.isTimelineDataLoaded,
                        isTimelineRefreshing = state.isTimelineDataLoaded,
                        showTimeline = true,
                        errorMessage = null,
                        refreshErrorMessage = null,
                        timelineErrorMessage = null,
                        timelineRefreshErrorMessage = null,
                    )
                }
                return null
            }

            AuthSessionState.Unauthenticated,
            AuthSessionState.ReauthenticationRequired -> {
                showActivityError(
                    "로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요.",
                    canRetry = false,
                )
                return null
            }

            AuthSessionState.Authenticated -> Unit
        }

        val identity = observedAuthIdentity
        if (identity == null || !isLiveSession(observedAuthState, identity)) {
            showActivityError("로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요.", canRetry = false)
            return null
        }

        return ActivityRequestContext(
            roomKey = roomKey,
            roomId = numericRoomId,
            activityDate = activityDate,
            generation = requestGeneration,
            identity = identity,
        )
    }

    private fun handleMemberFailure(preserveMembers: Boolean) {
        isMemberAutoPollingStopped = true
        mutableUiState.update { state ->
            if (preserveMembers && state.isDataLoaded) {
                state.copy(
                    isInitialLoading = false,
                    isRefreshing = false,
                    refreshErrorMessage = "회원 이동 상태를 갱신하지 못했어요. 다시 시도해 주세요.",
                )
            } else {
                state.copy(
                    members = emptyList(),
                    selectedMemberIds = null,
                    isInitialLoading = false,
                    isRefreshing = false,
                    isDataLoaded = false,
                    errorMessage = "회원 이동 상태를 불러오지 못했어요. 다시 시도해 주세요.",
                    refreshErrorMessage = null,
                    canRetry = true,
                )
            }
        }
        stopPollingIfNoAutomaticRefreshRemains()
    }

    private fun handleTimelineFailure(requestedDate: MissionDate?) {
        isTimelineAutoPollingStopped = true
        mutableUiState.update { state ->
            val canKeepRecords = state.isTimelineDataLoaded && state.timelineDate == requestedDate
            if (canKeepRecords) {
                state.copy(
                    isTimelineInitialLoading = false,
                    isTimelineRefreshing = false,
                    timelineRefreshErrorMessage = "활동 기록을 갱신하지 못했어요. 다시 시도해 주세요.",
                )
            } else {
                state.copy(
                    timeline = emptyList(),
                    isTimelineInitialLoading = false,
                    isTimelineRefreshing = false,
                    isTimelineDataLoaded = false,
                    timelineErrorMessage = "활동 기록을 불러오지 못했어요. 다시 시도해 주세요.",
                    timelineRefreshErrorMessage = null,
                    canRetryTimeline = true,
                )
            }
        }
        stopPollingIfNoAutomaticRefreshRemains()
    }

    private fun showAccessFailure(error: RoomRepositoryException) {
        invalidateRequests()
        stopPollingPermanently()
        val message = error.activityAccessMessage()
        mutableUiState.update { state ->
            state.copy(
                members = emptyList(),
                timeline = emptyList(),
                selectedMemberIds = null,
                timelineDate = null,
                isInitialLoading = false,
                isRefreshing = false,
                isDataLoaded = false,
                errorMessage = message,
                refreshErrorMessage = null,
                canRetry = false,
                showTimeline = false,
                isTimelineInitialLoading = false,
                isTimelineRefreshing = false,
                isTimelineDataLoaded = false,
                timelineErrorMessage = null,
                timelineRefreshErrorMessage = null,
                canRetryTimeline = false,
            )
        }
    }

    private fun showActivityError(message: String, canRetry: Boolean) {
        invalidateRequests()
        stopPollingPermanently()
        mutableUiState.update { state ->
            state.copy(
                members = emptyList(),
                timeline = emptyList(),
                selectedMemberIds = null,
                timelineDate = null,
                isInitialLoading = false,
                isRefreshing = false,
                isDataLoaded = false,
                errorMessage = message,
                refreshErrorMessage = null,
                canRetry = canRetry,
                showTimeline = false,
                isTimelineInitialLoading = false,
                isTimelineRefreshing = false,
                isTimelineDataLoaded = false,
                timelineErrorMessage = null,
                timelineRefreshErrorMessage = null,
                canRetryTimeline = false,
            )
        }
    }

    private fun startPolling() {
        if (
            pollingJob?.isActive == true ||
            (isMemberAutoPollingStopped && isTimelineAutoPollingStopped) ||
            !hasValidRoute() ||
            !isLiveSession(observedAuthState, observedAuthIdentity)
        ) return

        pollingJob = scope.launch {
            while (
                isActive && isResumed && hasValidRoute() &&
                !(isMemberAutoPollingStopped && isTimelineAutoPollingStopped)
            ) {
                delay(PollingIntervalMillis)
                if (!isActive || !isResumed) break
                if (!isMemberAutoPollingStopped) requestMembers()
                if (!isTimelineAutoPollingStopped) requestRecords()
            }
            if (pollingJob === currentCoroutineContext()[Job]) pollingJob = null
        }
    }

    private fun stopPollingIfNoAutomaticRefreshRemains() {
        if (isMemberAutoPollingStopped && isTimelineAutoPollingStopped) stopPolling()
    }

    private fun stopPollingPermanently() {
        isMemberAutoPollingStopped = true
        isTimelineAutoPollingStopped = true
        stopPolling()
    }

    private fun resetAutoPollingStops() {
        isMemberAutoPollingStopped = false
        isTimelineAutoPollingStopped = false
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun invalidateRequests() {
        requestGeneration += 1L
        membersJob?.cancel()
        recordsJob?.cancel()
        membersJob = null
        recordsJob = null
    }

    private fun hasValidRoute(): Boolean =
        activeRoomId?.toLongOrNull()?.takeIf { it > 0L } != null && activeActivityDate != null

    private fun isCurrentRequest(context: ActivityRequestContext): Boolean =
        requestGeneration == context.generation &&
            activeRoomId == context.roomKey &&
            activeActivityDate == context.activityDate &&
            observedAuthState == AuthSessionState.Authenticated &&
            observedAuthIdentity === context.identity &&
            isLiveSession(AuthSessionState.Authenticated, context.identity)

    private fun isLiveSession(authState: AuthSessionState?, identity: Any?): Boolean =
        authState == AuthSessionState.Authenticated &&
            identity != null &&
            authSession.state.value == authState &&
            authSession.identity.value === identity

    override fun onCleared() {
        invalidateRequests()
        stopPolling()
        super.onCleared()
    }

    private data class ActivityRequestContext(
        val roomKey: String,
        val roomId: Long,
        val activityDate: MissionDate,
        val generation: Long,
        val identity: Any,
    )
}

private fun RoomRepositoryException.isPermanentActivityFailure(): Boolean =
    statusCode in setOf(400, 401, 403, 404) ||
        code in setOf(
            "ROOM400", "RECORD400", "COMMON400", "AUTH401", "ROOM403", "RECORD403", "COMMON403",
            "ROOM404", "RECORD404", "COMMON404",
        )

private fun RoomRepositoryException.activityAccessMessage(): String = when {
    statusCode == 401 || code == "AUTH401" -> "로그인이 필요해요. 뒤로 이동한 뒤 로그인 상태를 확인해 주세요."
    statusCode == 403 || code in setOf("ROOM403", "RECORD403", "COMMON403") ->
        "모임 참여 권한이 없어 활동 정보를 볼 수 없어요."
    statusCode == 404 || code in setOf("ROOM404", "RECORD404", "COMMON404") ->
        "삭제되었거나 찾을 수 없는 모임이에요."
    else -> "모임 활동 정보가 올바르지 않아요."
}

private fun String.toMissionDateOrNull(): MissionDate? = runCatching {
    MissionDate.parse(this)
}.getOrNull()

internal const val RoomActivityPollingIntervalMillis = 10_000L

private const val PollingIntervalMillis = RoomActivityPollingIntervalMillis
