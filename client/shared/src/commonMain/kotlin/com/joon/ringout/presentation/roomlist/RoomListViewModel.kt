package com.joon.ringout.presentation.roomlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.presentation.roomhome.RoomHomeMemberUiModel
import com.joon.ringout.presentation.roomhome.RoomHomeRecordsUiState
import com.joon.ringout.presentation.roomhome.RoomHomeUiState
import com.joon.ringout.presentation.roomlist.model.RoomListUiState
import com.joon.ringout.presentation.roomlist.model.RoomMutationSource
import com.joon.ringout.presentation.roomlist.model.RoomMutationSuccess
import com.joon.ringout.presentation.roomlist.model.RoomMutationType
import com.joon.ringout.presentation.roomlist.model.RoomMutationUiState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.presentation.roomlist.model.toRoomUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RoomListViewModel(
    private val loadRooms: suspend () -> Result<List<RoomUiModel>>,
    private val createRoom: suspend (RoomCreateInput) -> Result<RoomMembershipDetails> = {
        Result.failure(IllegalStateException("모임 생성을 사용할 수 없어요."))
    },
    private val joinRoom: suspend (Long) -> Result<RoomMembershipDetails> = {
        Result.failure(IllegalStateException("모임 가입을 사용할 수 없어요."))
    },
    private val authSession: AuthSession? = null,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope

    var uiState by mutableStateOf(RoomListUiState())
        private set

    internal var mutationState by mutableStateOf(RoomMutationUiState())
        private set

    private data class SessionKey(val state: AuthSessionState, val identity: Any?)

    private var lastSessionKey: SessionKey? = null
    private var hasReceivedSession = false
    private var roomsRequestId = 0L
    private var mutationRequestId = 0L
    private var roomsJob: Job? = null
    private var mutationJob: Job? = null
    private var visibleMutationEntryId: Long? = null
    private val roomHomeDetails = mutableMapOf<String, RoomMembershipDetails>()

    internal val isRoomListUninitialized: Boolean
        get() = !hasReceivedSession

    internal fun onRouteVisible(authSessionState: AuthSessionState, identity: Any? = null) {
        onAuthSessionChanged(authSessionState, identity)
    }

    internal fun onAuthSessionChanged(authSessionState: AuthSessionState, identity: Any?) {
        val newSession = SessionKey(authSessionState, identity)
        if (hasReceivedSession && lastSessionKey == newSession) return

        val hadSession = hasReceivedSession
        hasReceivedSession = true
        lastSessionKey = newSession
        if (hadSession) {
            roomsJob?.cancel()
            mutationJob?.cancel()
            roomsRequestId += 1
            mutationRequestId += 1
            roomHomeDetails.clear()
            mutationState = RoomMutationUiState()
        }

        uiState = uiState.copy(
            allRooms = if (hadSession) emptyList() else uiState.allRooms,
            joinedRooms = emptyList(),
            isLoadingAllRooms = false,
            allRoomsErrorMessage = null,
            isRefreshingAllRooms = false,
            allRoomsRefreshErrorMessage = null,
            isAuthenticated = authSessionState == AuthSessionState.Authenticated,
        )
        if (authSessionState != AuthSessionState.Restoring) requestRooms()
    }

    internal fun onMutationSourceVisible(entryId: Long) {
        visibleMutationEntryId = entryId
    }

    internal fun onMutationSourceHidden(entryId: Long) {
        if (visibleMutationEntryId == entryId) visibleMutationEntryId = null
    }

    internal fun isCurrentMutationSource(entryId: Long): Boolean =
        visibleMutationEntryId == entryId

    internal fun onRetryRooms() {
        if (hasReceivedSession && lastSessionKey?.state != AuthSessionState.Restoring) requestRooms()
    }

    internal fun createRoom(source: RoomMutationSource, input: RoomCreateInput) {
        if (source.type != RoomMutationType.Create || source.entryId != visibleMutationEntryId) return
        startMutation(source) { createRoom(input) }
    }

    internal fun joinRoom(source: RoomMutationSource) {
        if (source.type != RoomMutationType.Join || source.entryId != visibleMutationEntryId) return
        val roomId = source.roomId?.toLongOrNull()?.takeIf { it > 0L }
        if (roomId == null) {
            setImmediateMutationError(source, RoomIdInvalidMessage, errorCode = RoomIdInvalidCode)
            return
        }
        startMutation(source) { joinRoom(roomId) }
    }

    internal fun consumeSuccessfulMutation(operationId: Long): RoomMutationSuccess? {
        val state = mutationState
        val source = state.source ?: return null
        val roomId = state.roomId ?: return null
        if (state.operationId != operationId || !state.isSuccessful || state.successConsumed) return null
        mutationState = state.copy(successConsumed = true)
        return RoomMutationSuccess(source = source, roomId = roomId)
    }

    internal fun roomHomeInitialState(roomId: String): RoomHomeUiState {
        if (!isLiveSessionSnapshotCurrent()) return RoomHomeUiState(isLoading = true)
        val details = roomHomeDetails[roomId]
        val currentRoom = uiState.allRooms.firstOrNull { it.id == roomId }
        val room = when {
            details != null && currentRoom != null -> details.room.toRoomUiModel().copy(
                isJoined = currentRoom.isJoined,
                participantCount = currentRoom.participantCount,
            )
            details != null -> details.room.toRoomUiModel()
            else -> currentRoom
        }
        return RoomHomeUiState(
            room = room,
            members = details?.members.orEmpty().map { member ->
                RoomHomeMemberUiModel(id = member.userId.toString(), nickname = member.nickname)
            },
            areMembersLoaded = details != null,
            isLoading = room == null && (isRoomListUninitialized || uiState.isLoadingAllRooms),
            errorMessage = if (room == null && !isRoomListUninitialized && !uiState.isLoadingAllRooms) {
                uiState.allRoomsErrorMessage ?: "모임 정보를 불러올 수 없어요."
            } else {
                null
            },
            recordsState = RoomHomeRecordsUiState(
                canViewRecords = room?.isJoined == true,
                isDataLoaded = false,
            ),
        )
    }

    private fun startMutation(
        source: RoomMutationSource,
        operation: suspend () -> Result<RoomMembershipDetails>,
    ) {
        if (mutationState.isInProgress) return
        val session = lastSessionKey
        if (
            session?.state != AuthSessionState.Authenticated ||
            session.identity == null ||
            !isCurrentSession(session)
        ) {
            setImmediateMutationError(source, RoomAuthenticationRequiredMessage, errorCode = RoomAuthRequiredCode)
            return
        }

        val operationId = ++mutationRequestId
        mutationState = RoomMutationUiState(
            operationId = operationId,
            source = source,
            isInProgress = true,
        )
        mutationJob = scope.launch {
            try {
                val details = operation().getOrThrow()
                if (!isCurrentMutation(operationId, session)) return@launch

                val room = details.room.toRoomUiModel()
                roomHomeDetails[room.id] = details
                invalidateRoomsRequest()
                upsertRoom(room)
                mutationState = RoomMutationUiState(
                    operationId = operationId,
                    source = source,
                    roomId = room.id,
                    isSuccessful = true,
                )
                requestRooms()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentMutation(operationId, session)) return@launch
                val (errorCode, message) = mutationMessage(source.type, error)
                mutationState = RoomMutationUiState(
                    operationId = operationId,
                    source = source,
                    roomId = source.roomId,
                    errorCode = errorCode,
                    errorMessage = message,
                )
                if (
                    errorCode in RoomRefreshAfterFailureCodes ||
                    error !is RoomRepositoryException || error.statusCode >= 500
                ) {
                    requestRooms()
                }
            }
        }
    }

    private fun setImmediateMutationError(
        source: RoomMutationSource,
        message: String,
        errorCode: String,
    ) {
        if (mutationState.isInProgress) return
        mutationState = RoomMutationUiState(
            operationId = ++mutationRequestId,
            source = source,
            roomId = source.roomId,
            errorCode = errorCode,
            errorMessage = message,
        )
    }

    private fun isCurrentMutation(operationId: Long, session: SessionKey): Boolean =
        mutationState.operationId == operationId && isCurrentSession(session)

    private fun isCurrentSession(session: SessionKey): Boolean {
        val liveSession = authSession?.let { SessionKey(it.state.value, it.identity.value) }
        return if (liveSession != null) liveSession == session else lastSessionKey == session
    }

    private fun isLiveSessionSnapshotCurrent(): Boolean {
        val currentSession = lastSessionKey ?: return authSession == null
        return isCurrentSession(currentSession)
    }

    private fun invalidateRoomsRequest() {
        roomsRequestId += 1
        roomsJob?.cancel()
        roomsJob = null
        uiState = uiState.copy(
            isLoadingAllRooms = false,
            isRefreshingAllRooms = false,
        )
    }

    private fun upsertRoom(room: RoomUiModel) {
        val existingIndex = uiState.allRooms.indexOfFirst { it.id == room.id }
        val allRooms = if (existingIndex < 0) {
            listOf(room) + uiState.allRooms.filterNot { it.id == room.id }
        } else {
            uiState.allRooms.mapIndexed { index, existing ->
                if (index == existingIndex) room else existing
            }.distinctBy(RoomUiModel::id)
        }
        uiState = uiState.copy(
            allRooms = allRooms,
            joinedRooms = allRooms.filter(RoomUiModel::isJoined),
            allRoomsErrorMessage = null,
            allRoomsRefreshErrorMessage = null,
        )
    }

    private fun requestRooms() {
        val requestId = ++roomsRequestId
        val requestSession = lastSessionKey ?: return
        roomsJob?.cancel()
        val hasExistingRooms = uiState.allRooms.isNotEmpty()
        uiState = uiState.copy(
            isLoadingAllRooms = !hasExistingRooms,
            allRoomsErrorMessage = if (hasExistingRooms) null else uiState.allRoomsErrorMessage,
            isRefreshingAllRooms = hasExistingRooms,
            allRoomsRefreshErrorMessage = null,
        )
        roomsJob = scope.launch {
            try {
                val rooms = loadRooms().getOrThrow()
                if (requestId != roomsRequestId || !isCurrentSession(requestSession)) return@launch
                val uniqueRooms = rooms.distinctBy(RoomUiModel::id)
                uiState = uiState.copy(
                    allRooms = uniqueRooms,
                    joinedRooms = uniqueRooms.filter(RoomUiModel::isJoined),
                    isLoadingAllRooms = false,
                    allRoomsErrorMessage = null,
                    isRefreshingAllRooms = false,
                    allRoomsRefreshErrorMessage = null,
                )
                if (mutationState.errorCode == RoomAlreadyJoinedCode) {
                    val isJoined = uniqueRooms.any { it.id == mutationState.roomId && it.isJoined }
                    mutationState = mutationState.copy(
                        isMembershipConfirmed = isJoined,
                        errorMessage = if (isJoined) {
                            RoomAlreadyJoinedConfirmedMessage
                        } else {
                            RoomAlreadyJoinedMessage
                        },
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                if (requestId != roomsRequestId || !isCurrentSession(requestSession)) return@launch
                val hasExisting = uiState.allRooms.isNotEmpty()
                uiState = uiState.copy(
                    isLoadingAllRooms = false,
                    allRoomsErrorMessage = if (hasExisting) null else RoomListLoadErrorMessage,
                    isRefreshingAllRooms = false,
                    allRoomsRefreshErrorMessage = if (hasExisting) RoomListRefreshErrorMessage else null,
                )
            }
        }
    }

    private fun mutationMessage(type: RoomMutationType, error: Throwable): Pair<String, String> {
        val apiError = error as? RoomRepositoryException
        val code = apiError?.code ?: when {
            apiError?.statusCode == 400 -> CommonBadRequestCode
            apiError?.statusCode == 401 -> CommonUnauthorizedCode
            apiError?.statusCode == 403 -> CommonForbiddenCode
            apiError?.statusCode == 404 -> CommonNotFoundCode
            apiError?.statusCode == 409 -> RoomAlreadyJoinedCode
            else -> RoomMutationUnknownErrorCode
        }
        val message = when {
            code == RoomAlreadyJoinedCode -> RoomAlreadyJoinedMessage
            code == RoomNotFoundCode || code == CommonNotFoundCode -> RoomNotFoundMessage
            code == RoomJoinForbiddenCode || code == CommonForbiddenCode -> RoomJoinForbiddenMessage
            code == RoomAuthenticationRequiredCode || code == CommonUnauthorizedCode -> RoomAuthenticationRequiredMessage
            code == RoomBadRequestCode || code == CommonBadRequestCode -> RoomBadRequestMessage
            apiError == null || (apiError.statusCode >= 500) -> RoomMutationUnconfirmedMessage
            type == RoomMutationType.Create -> RoomCreateFailedMessage
            else -> RoomJoinFailedMessage
        }
        return code to message
    }
}

internal const val RoomListLoadErrorMessage = "모임 목록을 불러오는 중 문제가 발생했어요."
internal const val RoomListRefreshErrorMessage = "모임 목록을 갱신하지 못했어요. 현재 목록은 유지하고 있어요."

private const val RoomIdInvalidCode = "ROOM_ID_INVALID"
private const val RoomAuthRequiredCode = "ROOM_AUTH_REQUIRED"
private const val RoomBadRequestCode = "ROOM400"
private const val RoomAuthenticationRequiredCode = "ROOM401"
private const val RoomJoinForbiddenCode = "ROOM403"
private const val RoomNotFoundCode = "ROOM404"
private const val RoomAlreadyJoinedCode = "ROOM409"
private const val CommonBadRequestCode = "COMMON400"
private const val CommonUnauthorizedCode = "COMMON401"
private const val CommonForbiddenCode = "COMMON403"
private const val CommonNotFoundCode = "COMMON404"
private const val RoomMutationUnknownErrorCode = "ROOM_MUTATION_ERROR"
private val RoomRefreshAfterFailureCodes = setOf(
    RoomNotFoundCode,
    RoomAlreadyJoinedCode,
    RoomMutationUnknownErrorCode,
)

private const val RoomAuthenticationRequiredMessage = "로그인이 필요해요. 로그인 상태를 확인한 뒤 다시 시도해 주세요."
private const val RoomBadRequestMessage = "입력한 모임 정보를 확인해 주세요."
private const val RoomJoinForbiddenMessage = "이 모임에는 가입할 수 없어요."
private const val RoomNotFoundMessage = "모임을 찾을 수 없어요. 모임 목록을 갱신해 주세요."
private const val RoomAlreadyJoinedMessage = "이미 참여 중인 모임인지 확인하고 있어요."
private const val RoomAlreadyJoinedConfirmedMessage = "이미 참여 중인 모임이에요. 목록의 가입 상태를 갱신했어요."
private const val RoomCreateFailedMessage = "모임을 생성하지 못했어요. 입력을 확인하고 다시 시도해 주세요."
private const val RoomJoinFailedMessage = "모임에 가입하지 못했어요. 다시 시도해 주세요."
private const val RoomMutationUnconfirmedMessage =
    "요청 결과를 확인할 수 없어요. 목록에서 저장 여부를 확인한 뒤 다시 시도해 주세요."
private const val RoomIdInvalidMessage = "모임 정보가 올바르지 않아 가입할 수 없어요."
