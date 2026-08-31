package com.joon.ringout.presentation.alarmsetup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.MissionLocationPermissionDecision
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.alarm.newAlarmId
import com.joon.ringout.alarm.permissionDecision
import com.joon.ringout.presentation.destination.DestinationSelection

class AlarmSetupViewModel(
    private val createAlarmId: () -> String = ::newAlarmId,
) : ViewModel() {
    internal sealed interface Command {
        data class ScheduleAlarm(
            val request: AlarmScheduleRequest,
        ) : Command

        data object RequestWhenInUseLocation : Command

        data object RequestAlwaysLocation : Command

        data object ConfirmAlwaysLocationResult : Command

        data object RequestTemporaryFullAccuracy : Command
    }

    var uiState by mutableStateOf(AlarmSetupUiState())
        private set

    internal var hasDraft by mutableStateOf(false)
        private set

    internal var permissionDialog by mutableStateOf<MissionLocationPermissionDecision?>(null)
        private set

    private var didRequestFullAccuracy = false
    private var isCleared = false

    fun startCreating(initialTime: String) {
        if (isCleared) return
        resetPermissionFlow()
        uiState = AlarmSetupUiState(time = initialTime)
        hasDraft = true
    }

    fun startEditing(request: AlarmScheduleRequest) {
        if (isCleared) return
        resetPermissionFlow()
        uiState = AlarmSetupUiState(
            alarmId = request.id,
            time = request.time,
            selectedDays = if (request.repeatEnabled) request.selectedDays else emptyList(),
            limitMinutes = request.limitMinutes.coerceIn(
                minimumValue = MinAlarmLimitMinutes,
                maximumValue = MaxAlarmLimitMinutes,
            ),
            destination = DestinationSelection(
                name = request.destinationName,
                address = request.destinationAddress,
                latitude = request.destinationLatitude,
                longitude = request.destinationLongitude,
            ),
            alarmSound = AlarmSoundSelection(
                name = request.alarmSoundName,
                uri = request.alarmSoundUri,
            ),
        )
        hasDraft = true
    }

    fun updateAmPm(isAm: Boolean) {
        updateTime { copy(isAm = isAm) }
    }

    fun updateHour(hour: Int) {
        updateTime { copy(hour = hour) }
    }

    fun updateMinute(minute: Int) {
        updateTime { copy(minute = minute) }
    }

    fun toggleDay(day: String) {
        if (isCleared || day !in AlarmSetupWeekdays) return

        val selectedDays = uiState.selectedDays.toSet().let { currentDays ->
            if (day in currentDays) currentDays - day else currentDays + day
        }
        uiState = uiState.copy(
            selectedDays = AlarmSetupWeekdays.filter(selectedDays::contains),
        )
    }

    fun updateLimitMinutes(minutes: Int) {
        if (isCleared) return
        uiState = uiState.copy(
            limitMinutes = minutes.coerceIn(
                minimumValue = MinAlarmLimitMinutes,
                maximumValue = MaxAlarmLimitMinutes,
            ),
        )
    }

    fun updateDestination(destination: DestinationSelection) {
        if (isCleared) return
        uiState = uiState.copy(destination = destination)
    }

    fun updateAlarmSound(alarmSound: AlarmSoundSelection) {
        if (isCleared) return
        uiState = uiState.copy(alarmSound = alarmSound)
    }

    fun requestSave(): Boolean {
        if (isCleared || uiState.isSaveInProgress) return false
        val request = createScheduleRequest() ?: return false

        resetPermissionFlow()
        uiState = uiState.copy(
            pendingSaveRequest = request,
            isScheduling = false,
            errorMessage = null,
        )
        return true
    }

    internal fun onLocationStateChanged(
        locationState: MissionLocationState,
        useSystemPermissionUiOnly: Boolean,
        canProcessSave: Boolean,
    ): Command? {
        val request = uiState.pendingSaveRequest ?: return null
        if (!canProcessSave || uiState.isScheduling) return null

        return when (
            val decision = locationState.permissionDecision(
                didRequestFullAccuracy = didRequestFullAccuracy,
            )
        ) {
            MissionLocationPermissionDecision.READY -> {
                uiState = uiState.copy(isScheduling = true)
                resetPermissionFlow()
                Command.ScheduleAlarm(request)
            }

            MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE ->
                requestPermission(
                    decision = decision,
                    useSystemPermissionUiOnly = useSystemPermissionUiOnly,
                    command = Command.RequestWhenInUseLocation,
                )

            MissionLocationPermissionDecision.EXPLAIN_ALWAYS ->
                requestPermission(
                    decision = decision,
                    useSystemPermissionUiOnly = useSystemPermissionUiOnly,
                    command = Command.RequestAlwaysLocation,
                )

            MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT -> {
                permissionDialog = decision.takeUnless { useSystemPermissionUiOnly }
                null
            }

            MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY ->
                if (useSystemPermissionUiOnly) {
                    permissionDialog = null
                    didRequestFullAccuracy = true
                    Command.RequestTemporaryFullAccuracy
                } else {
                    permissionDialog = decision
                    null
                }

            MissionLocationPermissionDecision.SERVICES_DISABLED -> {
                failSave(LocationServicesDisabledMessage)
                null
            }

            MissionLocationPermissionDecision.DENIED -> {
                failSave(LocationPermissionDeniedMessage)
                null
            }

            MissionLocationPermissionDecision.ALWAYS_REQUEST_FAILED -> {
                failSave(LocationPermissionCheckFailedMessage)
                Command.ConfirmAlwaysLocationResult
            }

            MissionLocationPermissionDecision.RESTRICTED -> {
                failSave(LocationPermissionRestrictedMessage)
                null
            }
        }
    }

    internal fun onPermissionDialogConfirmed(): Command? {
        val decision = permissionDialog ?: return null
        permissionDialog = null
        if (decision == MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY) {
            didRequestFullAccuracy = true
        }

        return when (decision) {
            MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE ->
                Command.RequestWhenInUseLocation

            MissionLocationPermissionDecision.EXPLAIN_ALWAYS ->
                Command.RequestAlwaysLocation

            MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT ->
                Command.ConfirmAlwaysLocationResult

            MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY ->
                Command.RequestTemporaryFullAccuracy

            MissionLocationPermissionDecision.READY,
            MissionLocationPermissionDecision.SERVICES_DISABLED,
            MissionLocationPermissionDecision.DENIED,
            MissionLocationPermissionDecision.ALWAYS_REQUEST_FAILED,
            MissionLocationPermissionDecision.RESTRICTED,
            -> null
        }
    }

    fun onPermissionDialogDismissed() {
        clearSaveFlow()
    }

    fun onSaveCompleted(request: AlarmScheduleRequest): Boolean {
        // 두 플랫폼의 컨트롤러는 전달받은 요청 인스턴스를 그대로 반환한다.
        // 식별자가 같아도 새 편집 흐름의 요청일 수 있으므로 인스턴스를 비교한다.
        if (uiState.pendingSaveRequest !== request) return false

        clearSaveFlow(errorMessage = null)
        return true
    }

    fun onSaveError(request: AlarmScheduleRequest, message: String) {
        if (uiState.pendingSaveRequest !== request) return

        clearSaveFlow(errorMessage = message)
    }

    fun showError(message: String) {
        if (isCleared) return
        uiState = uiState.copy(errorMessage = message)
    }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    fun resetSaveFlow() {
        clearSaveFlow(errorMessage = null)
    }

    fun createScheduleRequest(): AlarmScheduleRequest? {
        if (isCleared) return null
        val draft = uiState
        val destination = draft.destination?.takeIf { draft.canSave } ?: return null

        return AlarmScheduleRequest(
            id = draft.alarmId ?: createAlarmId(),
            time = draft.time,
            selectedDays = draft.selectedDays,
            repeatEnabled = draft.repeatEnabled,
            limitMinutes = draft.limitMinutes,
            destinationName = destination.name,
            destinationAddress = destination.address,
            destinationLatitude = destination.latitude,
            destinationLongitude = destination.longitude,
            alarmSoundName = draft.alarmSound.name,
            alarmSoundUri = draft.alarmSound.uri,
        )
    }

    override fun onCleared() {
        isCleared = true
        resetPermissionFlow()
        hasDraft = false
        uiState = AlarmSetupUiState()
        super.onCleared()
    }

    private inline fun updateTime(
        transform: AlarmTimePickerValue.() -> AlarmTimePickerValue,
    ) {
        if (isCleared) return
        uiState = uiState.copy(
            time = uiState.time
                .toAlarmTimePickerValue()
                .transform()
                .to24HourString(),
        )
    }

    private fun requestPermission(
        decision: MissionLocationPermissionDecision,
        useSystemPermissionUiOnly: Boolean,
        command: Command,
    ): Command? = if (useSystemPermissionUiOnly) {
        permissionDialog = null
        command
    } else {
        permissionDialog = decision
        null
    }

    private fun failSave(message: String) {
        clearSaveFlow(errorMessage = message)
    }

    private fun clearSaveFlow(errorMessage: String? = uiState.errorMessage) {
        resetPermissionFlow()
        uiState = uiState.copy(
            pendingSaveRequest = null,
            isScheduling = false,
            errorMessage = errorMessage,
        )
    }

    private fun resetPermissionFlow() {
        permissionDialog = null
        didRequestFullAccuracy = false
    }
}

internal const val LocationServicesDisabledMessage =
    "위치 서비스가 꺼져 있어 목적지 알람을 시작할 수 없습니다."
internal const val LocationPermissionDeniedMessage =
    "위치 권한이 없어 목적지 알람을 시작할 수 없습니다."
internal const val LocationPermissionCheckFailedMessage =
    "위치 권한 상태를 확인하지 못했습니다. 다시 시도해 주세요."
internal const val LocationPermissionRestrictedMessage =
    "이 기기에서는 위치 사용이 제한되어 있습니다."
