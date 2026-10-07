package com.joon.ringout.presentation.roomedit

import com.joon.ringout.presentation.alarmsetup.AlarmTimePickerValue
import com.joon.ringout.presentation.alarmsetup.toAlarmTimePickerValue
import com.joon.ringout.presentation.alarmsetup.to24HourString
import com.joon.ringout.presentation.common.WeekdayOrder
import com.joon.ringout.analytics.ProductAnalyticsRecorder
import com.joon.ringout.analytics.RoomAnalyticsEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.room.RoomImageUpload
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomRepositoryException
import com.joon.ringout.domain.room.RoomUpdateInput
import com.joon.ringout.domain.room.RoomUpdateResult
import com.joon.ringout.presentation.roomedit.model.RoomEditSuccessfulUpdate
import com.joon.ringout.presentation.roomedit.model.RoomEditUiState
import com.joon.ringout.presentation.roomedit.model.validateRoomEditName
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import com.joon.ringout.presentation.roomlist.model.toRoomUiModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

internal class RoomEditViewModel(
    private val loadRoom: suspend (Long) -> RoomMembershipDetails = {
        throw IllegalStateException("모임 상세 조회를 사용할 수 없어요.")
    },
    private val updateRoom: suspend (Long, RoomUpdateInput) -> RoomUpdateResult = { _, _ ->
        throw IllegalStateException("모임 수정을 사용할 수 없어요.")
    },
    private val authSession: AuthSession = AuthSession(),
    coroutineScope: CoroutineScope? = null,
    private val analytics: ProductAnalyticsRecorder? = null,
) : ViewModel() {
    private val scope = coroutineScope ?: viewModelScope
    private var observedAuthState = authSession.state.value
    private var observedAuthIdentity = authSession.identity.value
    private var activeRoomId: String? = null
    private var activeRouteAuthState: AuthSessionState? = null
    private var activeRouteIdentity: Any? = null
    private var loadGeneration = 0L
    private var loadJob: Job? = null
    private var saveGeneration = 0L
    private var saveJob: Job? = null
    private var selectedUpload: SelectedRoomImageUpload? = null
    private var completionId = 0L
    private var consumedCompletionId: Long? = null
    private var lastLoadedKey: RouteSessionKey? = null

    var uiState by mutableStateOf(RoomEditUiState())
        private set

    init {
        if (coroutineScope == null) {
            scope.launch {
                combine(authSession.state, authSession.identity) { state, identity -> state to identity }
                    .collect { (state, identity) -> onAuthSessionChanged(state, identity) }
            }
        }
    }

    fun onRouteVisible(roomId: String) {
        if (activeRoomId != roomId) {
            invalidateLoad()
            invalidateSave()
            selectedUpload = null
            lastLoadedKey = null
            uiState = RoomEditUiState(roomId = roomId)
        }
        activeRoomId = roomId
        refreshForCurrentSession()
    }

    fun onRetry() {
        val roomId = activeRoomId ?: return
        val numericRoomId = roomId.toPositiveRoomIdOrNull() ?: return
        val identity = activeRouteIdentity ?: return
        if (activeRouteAuthState != AuthSessionState.Authenticated || !isLiveSession(identity)) return
        requestRoom(numericRoomId, identity, force = true)
    }

    fun updateName(value: String) {
        if (uiState.isOriginalLoaded && !uiState.isSaving) {
            uiState = uiState.copy(nameInput = value, saveErrorMessage = uiState.saveErrorMessage.takeIf { uiState.isSaveBlocked })
        }
    }

    fun updateIntroduction(value: String) {
        if (uiState.isOriginalLoaded && !uiState.isSaving) {
            uiState = uiState.copy(
                introductionInput = value,
                saveErrorMessage = uiState.saveErrorMessage.takeIf { uiState.isSaveBlocked },
            )
        }
    }

    fun toggleDay(day: String) {
        if (!uiState.isOriginalLoaded || uiState.isSaving || day !in WeekdayOrder) return
        val current = uiState.selectedDays.toSet()
        val selected = if (day in current) current - day else current + day
        uiState = uiState.copy(selectedDays = WeekdayOrder.filter(selected::contains))
    }

    fun updateAmPm(isAm: Boolean) = updateTime { copy(isAm = isAm) }

    fun updateHour(hour: Int) = updateTime { copy(hour = hour) }

    fun updateMinute(minute: Int) = updateTime { copy(minute = minute) }

    private fun updateTime(transform: AlarmTimePickerValue.() -> AlarmTimePickerValue) {
        if (!uiState.isOriginalLoaded || uiState.isSaving) return
        uiState = uiState.copy(time24Hour = transform(uiState.time24Hour.toAlarmTimePickerValue()).to24HourString())
    }

    fun onImageSelected(selectionToken: Long, upload: RoomImageUpload) {
        if (uiState.isOriginalLoaded && !uiState.isSaving) {
            selectedUpload = SelectedRoomImageUpload(selectionToken, upload)
            uiState = uiState.copy(
                imageSelectionToken = selectionToken,
                saveErrorMessage = uiState.saveErrorMessage.takeIf { uiState.isSaveBlocked },
            )
        }
    }

    fun onImagePreviewLost(selectionToken: Long) {
        if (uiState.imageSelectionToken == selectionToken && !uiState.isSaving) {
            selectedUpload = null
            uiState = uiState.copy(imageSelectionToken = null)
        }
    }

    fun saveChanges() {
        val state = uiState
        if (!state.canSave || state.isSaving) return
        val roomId = activeRoomId?.toPositiveRoomIdOrNull() ?: return
        val identity = activeRouteIdentity ?: return
        if (activeRouteAuthState != AuthSessionState.Authenticated || !isLiveSession(identity)) return
        if (state.imageSelectionToken != null && selectedUpload?.selectionToken != state.imageSelectionToken) {
            uiState = state.copy(saveErrorMessage = "사진을 다시 선택해 주세요.")
            return
        }

        val input = state.toUpdateInput(selectedUpload)
        if (!input.hasChanges) return

        invalidateSave()
        val requestId = saveGeneration
        uiState = state.copy(isSaving = true, saveErrorMessage = null)
        saveJob = scope.launch {
            try {
                val result = updateRoom(roomId, input)
                if (!isCurrentSave(requestId, roomId, identity)) return@launch
                applySuccessfulUpdate(result, identity)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentSave(requestId, roomId, identity)) return@launch
                uiState = uiState.copy(
                    isSaving = false,
                    saveErrorMessage = saveErrorMessage(error),
                    isSaveBlocked = isSaveBlocked(error),
                )
            }
        }
    }

    fun consumeSuccessfulUpdate(completionId: Long): RoomEditSuccessfulUpdate? {
        val completion = uiState.successfulUpdate ?: return null
        if (completion.completionId != completionId || consumedCompletionId == completionId) return null
        consumedCompletionId = completionId
        uiState = uiState.copy(successfulUpdate = null)
        return completion
    }

    private fun refreshForCurrentSession() {
        val roomId = activeRoomId ?: return
        activeRouteAuthState = authSession.state.value
        activeRouteIdentity = authSession.identity.value

        val numericRoomId = roomId.toPositiveRoomIdOrNull()
        if (numericRoomId == null) {
            invalidateLoad()
            uiState = uiState.copy(
                roomId = roomId,
                isLoading = false,
                loadErrorMessage = "모임 정보를 확인할 수 없어요.",
                canRetryLoad = false,
            )
            return
        }

        when (activeRouteAuthState) {
            AuthSessionState.Restoring -> {
                invalidateLoad()
                uiState = uiState.copy(roomId = roomId, isLoading = true, loadErrorMessage = null, canRetryLoad = false)
            }
            AuthSessionState.Authenticated -> {
                val identity = activeRouteIdentity
                if (identity == null || !isLiveSession(identity)) {
                    invalidateLoad()
                    uiState = uiState.copy(roomId = roomId, isLoading = true, loadErrorMessage = null, canRetryLoad = false)
                } else {
                    requestRoom(numericRoomId, identity)
                }
            }
            AuthSessionState.Unauthenticated,
            AuthSessionState.ReauthenticationRequired,
            null -> {
                invalidateLoad()
                uiState = uiState.copy(
                    roomId = roomId,
                    isLoading = false,
                    loadErrorMessage = "로그인이 필요해요.",
                    canRetryLoad = false,
                )
            }
        }
    }

    private fun onAuthSessionChanged(authState: AuthSessionState, identity: Any?) {
        val changed = observedAuthState != authState || observedAuthIdentity !== identity
        if (!changed) return
        observedAuthState = authState
        observedAuthIdentity = identity
        activeRouteAuthState = authState
        activeRouteIdentity = identity
        invalidateLoad()
        invalidateSave()
        selectedUpload = null
        lastLoadedKey = null
        if (activeRoomId != null) {
            uiState = RoomEditUiState(roomId = activeRoomId)
            refreshForCurrentSession()
        }
    }

    private fun requestRoom(roomId: Long, identity: Any, force: Boolean = false) {
        val key = RouteSessionKey(roomId, identity)
        if (!force && lastLoadedKey == key && uiState.isOriginalLoaded) return
        if (!force && uiState.isLoading && activeRoomId?.toLongOrNull() == roomId) return

        invalidateLoad()
        val requestId = loadGeneration
        uiState = uiState.copy(
            roomId = roomId.toString(),
            isLoading = true,
            loadErrorMessage = null,
            canRetryLoad = false,
        )
        loadJob = scope.launch {
            try {
                val details = loadRoom(roomId)
                if (!isCurrentLoad(requestId, roomId, identity)) return@launch
                applyRoomDetails(details, roomId, key)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (!isCurrentLoad(requestId, roomId, identity)) return@launch
                uiState = uiState.copy(
                    isLoading = false,
                    loadErrorMessage = loadErrorMessage(error),
                    canRetryLoad = isRetryable(error),
                )
            }
        }
    }

    private fun applyRoomDetails(details: RoomMembershipDetails, roomId: Long, key: RouteSessionKey) {
        if (details.room.id != roomId) {
            uiState = uiState.copy(
                isLoading = false,
                loadErrorMessage = "조회한 모임 정보가 요청한 모임과 달라요. 다시 시도해 주세요.",
                canRetryLoad = true,
            )
            return
        }
        if (details.membershipRole != RoomMembershipRole.OWNER || !details.room.isJoined) {
            uiState = uiState.copy(
                isLoading = false,
                loadErrorMessage = "모임을 수정할 수 있는 방장 권한이 없어요.",
                canRetryLoad = false,
            )
            return
        }

        val original = details.room.toRoomUiModel()
        lastLoadedKey = key
        uiState = uiState.copy(
            roomId = roomId.toString(),
            original = original,
            selectedDays = original.activityDays,
            time24Hour = details.room.activityTime.take(5),
            originalTime24Hour = details.room.activityTime.take(5),
            nameInput = original.name,
            introductionInput = original.description,
            imageSelectionToken = null,
            isLoading = false,
            loadErrorMessage = null,
            canRetryLoad = false,
            isSaving = false,
            saveErrorMessage = null,
            isSaveBlocked = false,
            successfulUpdate = null,
        )
        selectedUpload = null
    }

    private fun applySuccessfulUpdate(result: RoomUpdateResult, identity: Any) {
        if (result.roomId != activeRoomId?.toLongOrNull()) {
            uiState = uiState.copy(
                isSaving = false,
            saveErrorMessage = "수정된 모임 정보가 요청한 모임과 달라요. 다시 시도해 주세요.",
            isSaveBlocked = false,
        )
            return
        }

        val original = uiState.original
        val updatedOriginal = original?.copy(
            representativeImage = result.imageUrl,
            name = result.name,
            description = result.description.orEmpty(),
        )
        selectedUpload = null
        val completion = RoomEditSuccessfulUpdate(++completionId, result, identity)
        consumedCompletionId = null
        uiState = uiState.copy(
            original = updatedOriginal,
            nameInput = result.name,
            introductionInput = result.description.orEmpty(),
            imageSelectionToken = null,
            isSaving = false,
            saveErrorMessage = null,
            isSaveBlocked = false,
            successfulUpdate = completion,
        )
        runCatching { analytics?.recordRoomEvent(RoomAnalyticsEvent.Updated(RoomMembershipRole.OWNER)) }
    }

    private fun RoomEditUiState.toUpdateInput(upload: SelectedRoomImageUpload?): RoomUpdateInput {
        val original = checkNotNull(original)
        val imageUpload = imageSelectionToken
            ?.let { token -> upload?.takeIf { it.selectionToken == token }?.upload }
            ?: return RoomUpdateInput(
                name = changedName(original),
                description = changedDescription(original),
                image = null,
            )
        return RoomUpdateInput(
            name = changedName(original),
            description = changedDescription(original),
            image = imageUpload,
        )
    }

    private fun RoomEditUiState.changedName(original: RoomUiModel): String? {
        val value = effectiveNameValidation.normalizedValue
        val originalValue = validateRoomEditName(original.name).normalizedValue
        return value.takeIf { it != originalValue }
    }

    private fun RoomEditUiState.changedDescription(original: RoomUiModel): String? =
        effectiveIntroduction.takeIf { it != original.description }

    private fun isCurrentLoad(requestId: Long, roomId: Long, identity: Any): Boolean =
        loadGeneration == requestId &&
            activeRoomId?.toLongOrNull() == roomId &&
            activeRouteAuthState == AuthSessionState.Authenticated &&
            activeRouteIdentity === identity &&
            isLiveSession(identity)

    private fun isCurrentSave(requestId: Long, roomId: Long, identity: Any): Boolean =
        saveGeneration == requestId &&
            activeRoomId?.toLongOrNull() == roomId &&
            activeRouteAuthState == AuthSessionState.Authenticated &&
            activeRouteIdentity === identity &&
            isLiveSession(identity)

    private fun isLiveSession(identity: Any): Boolean =
        observedAuthState == AuthSessionState.Authenticated &&
            observedAuthIdentity === identity &&
            authSession.state.value == AuthSessionState.Authenticated &&
            authSession.identity.value === identity

    private fun invalidateLoad() {
        loadGeneration += 1L
        loadJob?.cancel()
        loadJob = null
    }

    private fun invalidateSave() {
        saveGeneration += 1L
        saveJob?.cancel()
        saveJob = null
    }
}

private fun String.toPositiveRoomIdOrNull(): Long? = toLongOrNull()?.takeIf { it > 0L }

private fun loadErrorMessage(error: Throwable): String = when {
    error is RoomRepositoryException && error.isAuthError -> "로그인이 필요해요."
    error is RoomRepositoryException && error.isForbiddenError -> "모임을 수정할 수 있는 방장 권한이 없어요."
    error is RoomRepositoryException && error.isNotFoundError ->
        "모임이 삭제되었거나 찾을 수 없어요."
    else -> "모임 정보를 불러오지 못했어요. 다시 시도해 주세요."
}

private fun saveErrorMessage(error: Throwable): String = when {
    error is RoomRepositoryException && error.isAuthError -> "로그인이 필요해요."
    error is RoomRepositoryException && error.isForbiddenError -> "모임을 수정할 수 있는 방장 권한이 없어요."
    error is RoomRepositoryException && error.isNotFoundError ->
        "모임이 삭제되었거나 찾을 수 없어요."
    error is RoomRepositoryException && error.isBadRequestError && error.message.isNotBlank() -> error.message
    error is RoomRepositoryException && error.statusCode >= 500 -> "서버 문제로 모임 정보를 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
    else -> "모임 정보를 저장하지 못했어요. 상태를 확인하고 다시 시도해 주세요."
}

private fun isRetryable(error: Throwable): Boolean =
    error !is RoomRepositoryException || error.statusCode >= 500

private fun isSaveBlocked(error: Throwable): Boolean =
    error is RoomRepositoryException && (error.isForbiddenError || error.isNotFoundError)

private val RoomRepositoryException.isAuthError: Boolean
    get() = statusCode == 401 || code in setOf("AUTH401", "ROOM401", "COMMON401")

private val RoomRepositoryException.isForbiddenError: Boolean
    get() = statusCode == 403 || code in setOf("ROOM403", "COMMON403")

private val RoomRepositoryException.isNotFoundError: Boolean
    get() = statusCode == 404 || code in setOf("ROOM404", "COMMON404")

private val RoomRepositoryException.isBadRequestError: Boolean
    get() = statusCode == 400 || code in setOf("ROOM400", "COMMON400")

private data class RouteSessionKey(
    val roomId: Long,
    val identity: Any,
)

private data class SelectedRoomImageUpload(
    val selectionToken: Long,
    val upload: RoomImageUpload,
)
