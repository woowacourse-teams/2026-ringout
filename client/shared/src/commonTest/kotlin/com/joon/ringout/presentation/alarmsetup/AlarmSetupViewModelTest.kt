package com.joon.ringout.presentation.alarmsetup

import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.DefaultMissionLocationState
import com.joon.ringout.alarm.MissionLocationAccuracyState
import com.joon.ringout.alarm.MissionLocationAuthorizationState
import com.joon.ringout.alarm.MissionLocationPermissionDecision
import com.joon.ringout.alarm.MissionLocationServicesState
import com.joon.ringout.alarm.MissionLocationState
import com.joon.ringout.presentation.destination.DestinationSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmSetupViewModelTest {
    @Test
    fun `새 알람을 시작하면 현재 시각으로 초안을 초기화한다`() {
        val viewModel = AlarmSetupViewModel(createAlarmId = { "new-alarm" })
        viewModel.updateDestination(destination)

        viewModel.startCreating(initialTime = "13:07")

        assertEquals(
            AlarmSetupUiState(time = "13:07"),
            viewModel.uiState,
        )
        assertFalse(viewModel.uiState.isEditing)
        assertFalse(viewModel.uiState.canSave)
    }

    @Test
    fun `기존 알람 수정을 시작하면 저장된 값으로 초안을 초기화한다`() {
        val viewModel = AlarmSetupViewModel()
        val request = savedRequest.copy(repeatEnabled = false)

        viewModel.startEditing(request)

        assertEquals("alarm-1", viewModel.uiState.alarmId)
        assertEquals("08:30", viewModel.uiState.time)
        assertEquals(emptyList(), viewModel.uiState.selectedDays)
        assertEquals(17, viewModel.uiState.limitMinutes)
        assertEquals(destination, viewModel.uiState.destination)
        assertEquals(alarmSound, viewModel.uiState.alarmSound)
        assertTrue(viewModel.uiState.isEditing)
        assertTrue(viewModel.uiState.canSave)
    }

    @Test
    fun `시간과 요일을 변경하면 초안에 반영한다`() {
        val viewModel = AlarmSetupViewModel()
        viewModel.startCreating(initialTime = "06:20")

        viewModel.updateAmPm(isAm = false)
        viewModel.updateHour(hour = 7)
        viewModel.updateMinute(minute = 45)
        viewModel.toggleDay("월")

        assertEquals("19:45", viewModel.uiState.time)
        assertEquals(listOf("화", "수", "목", "금", "토", "일"), viewModel.uiState.selectedDays)
    }

    @Test
    fun `목적지와 알람음을 변경하면 초안에 반영한다`() {
        val viewModel = AlarmSetupViewModel()

        viewModel.updateDestination(destination)
        viewModel.updateAlarmSound(alarmSound)

        assertEquals(destination, viewModel.uiState.destination)
        assertEquals(alarmSound, viewModel.uiState.alarmSound)
        assertTrue(viewModel.uiState.canSave)
    }

    @Test
    fun `제한 시간을 범위 밖으로 변경하면 허용 범위로 보정한다`() {
        val viewModel = AlarmSetupViewModel()

        viewModel.updateLimitMinutes(minutes = 0)
        assertEquals(1, viewModel.uiState.limitMinutes)

        viewModel.updateLimitMinutes(minutes = 31)
        assertEquals(30, viewModel.uiState.limitMinutes)
    }

    @Test
    fun `목적지가 없으면 저장 요청을 만들 수 없다`() {
        val viewModel = AlarmSetupViewModel(createAlarmId = { "new-alarm" })

        assertNull(viewModel.createScheduleRequest())
    }

    @Test
    fun `유효한 초안은 알람 저장 요청으로 변환된다`() {
        val viewModel = AlarmSetupViewModel(createAlarmId = { "new-alarm" })
        viewModel.startCreating(initialTime = "07:40")
        viewModel.toggleDay("토")
        viewModel.toggleDay("일")
        viewModel.updateLimitMinutes(minutes = 20)
        viewModel.updateDestination(destination)
        viewModel.updateAlarmSound(alarmSound)

        val request = viewModel.createScheduleRequest()

        assertEquals(
            AlarmScheduleRequest(
                id = "new-alarm",
                time = "07:40",
                selectedDays = listOf("월", "화", "수", "목", "금"),
                repeatEnabled = true,
                limitMinutes = 20,
                destinationName = "회사",
                destinationAddress = "서울특별시 강남구 테헤란로 1",
                destinationLatitude = 37.4979,
                destinationLongitude = 127.0276,
                alarmSoundName = "파도",
                alarmSoundUri = "content://alarm/wave",
            ),
            request,
        )
    }

    @Test
    fun `저장 요청을 받으면 처리 중 상태가 되고 중복 요청을 거부한다`() {
        val viewModel = configuredViewModel()

        assertTrue(viewModel.requestSave())
        assertFalse(viewModel.requestSave())

        assertEquals(request, viewModel.uiState.pendingSaveRequest)
        assertTrue(viewModel.uiState.isSaveInProgress)
        assertFalse(viewModel.uiState.isScheduling)
    }

    @Test
    fun `위치 조건이 준비되면 알람 예약 명령을 반환한다`() {
        val viewModel = viewModelWithPendingSave()

        val command = viewModel.onLocationStateChanged(
            locationState = DefaultMissionLocationState,
            useSystemPermissionUiOnly = false,
            canProcessSave = true,
        )

        assertEquals(AlarmSetupViewModel.Command.ScheduleAlarm(request), command)
        assertTrue(viewModel.uiState.isScheduling)
        assertNull(viewModel.permissionDialog)
    }

    @Test
    fun `저장을 처리할 수 없는 화면에서는 권한 흐름을 시작하지 않는다`() {
        val viewModel = viewModelWithPendingSave()

        val command = viewModel.onLocationStateChanged(
            locationState = DefaultMissionLocationState,
            useSystemPermissionUiOnly = false,
            canProcessSave = false,
        )

        assertNull(command)
        assertFalse(viewModel.uiState.isScheduling)
        assertEquals(request, viewModel.uiState.pendingSaveRequest)
    }

    @Test
    fun `앱 권한 안내를 사용하는 경우 다이얼로그 확인 후 권한 요청 명령을 반환한다`() {
        val viewModel = viewModelWithPendingSave()

        val initialCommand = viewModel.onLocationStateChanged(
            locationState = locationState(
                authorization = MissionLocationAuthorizationState.NOT_DETERMINED,
            ),
            useSystemPermissionUiOnly = false,
            canProcessSave = true,
        )

        assertNull(initialCommand)
        assertEquals(
            MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE,
            viewModel.permissionDialog,
        )
        assertEquals(
            AlarmSetupViewModel.Command.RequestWhenInUseLocation,
            viewModel.onPermissionDialogConfirmed(),
        )
        assertNull(viewModel.permissionDialog)
    }

    @Test
    fun `시스템 권한 안내만 사용하는 경우 권한 요청 명령을 즉시 반환한다`() {
        val viewModel = viewModelWithPendingSave()

        val command = viewModel.onLocationStateChanged(
            locationState = locationState(
                authorization = MissionLocationAuthorizationState.WHEN_IN_USE,
            ),
            useSystemPermissionUiOnly = true,
            canProcessSave = true,
        )

        assertEquals(AlarmSetupViewModel.Command.RequestAlwaysLocation, command)
        assertNull(viewModel.permissionDialog)
    }

    @Test
    fun `정확한 위치 요청을 확인하면 같은 저장 흐름에서 한 번만 요청한다`() {
        val viewModel = viewModelWithPendingSave()
        val reducedAccuracyState = locationState(
            authorization = MissionLocationAuthorizationState.ALWAYS,
            accuracy = MissionLocationAccuracyState.REDUCED,
        )

        viewModel.onLocationStateChanged(
            locationState = reducedAccuracyState,
            useSystemPermissionUiOnly = false,
            canProcessSave = true,
        )
        val permissionCommand = viewModel.onPermissionDialogConfirmed()
        val scheduleCommand = viewModel.onLocationStateChanged(
            locationState = reducedAccuracyState,
            useSystemPermissionUiOnly = false,
            canProcessSave = true,
        )

        assertEquals(
            AlarmSetupViewModel.Command.RequestTemporaryFullAccuracy,
            permissionCommand,
        )
        assertEquals(AlarmSetupViewModel.Command.ScheduleAlarm(request), scheduleCommand)
    }

    @Test
    fun `권한 다이얼로그를 취소하면 대기 중인 저장 요청을 취소한다`() {
        val viewModel = viewModelWithPendingSave()
        viewModel.onLocationStateChanged(
            locationState = locationState(
                authorization = MissionLocationAuthorizationState.NOT_DETERMINED,
            ),
            useSystemPermissionUiOnly = false,
            canProcessSave = true,
        )

        viewModel.onPermissionDialogDismissed()

        assertFalse(viewModel.uiState.isSaveInProgress)
        assertNull(viewModel.uiState.pendingSaveRequest)
        assertNull(viewModel.permissionDialog)
    }

    @Test
    fun `위치 권한을 사용할 수 없으면 저장을 중단하고 원인 메시지를 제공한다`() {
        val cases = listOf(
            locationState(
                services = MissionLocationServicesState.DISABLED,
            ) to LocationServicesDisabledMessage,
            locationState(
                authorization = MissionLocationAuthorizationState.DENIED,
            ) to LocationPermissionDeniedMessage,
            locationState(
                authorization = MissionLocationAuthorizationState.RESTRICTED,
            ) to LocationPermissionRestrictedMessage,
        )

        cases.forEach { (locationState, expectedMessage) ->
            val viewModel = viewModelWithPendingSave()

            val command = viewModel.onLocationStateChanged(
                locationState = locationState,
                useSystemPermissionUiOnly = false,
                canProcessSave = true,
            )

            assertNull(command)
            assertFalse(viewModel.uiState.isSaveInProgress)
            assertEquals(expectedMessage, viewModel.uiState.errorMessage)
        }
    }

    @Test
    fun `항상 권한 확인 실패 시 저장을 중단하고 권한 결과 확인 명령을 반환한다`() {
        val viewModel = viewModelWithPendingSave()

        val command = viewModel.onLocationStateChanged(
            locationState = locationState(
                authorization = MissionLocationAuthorizationState.WHEN_IN_USE,
                alwaysAuthorizationRequestFailed = true,
            ),
            useSystemPermissionUiOnly = false,
            canProcessSave = true,
        )

        assertEquals(AlarmSetupViewModel.Command.ConfirmAlwaysLocationResult, command)
        assertFalse(viewModel.uiState.isSaveInProgress)
        assertEquals(
            LocationPermissionCheckFailedMessage,
            viewModel.uiState.errorMessage,
        )
    }

    @Test
    fun `현재 요청의 저장 완료만 처리하고 다른 요청의 결과는 무시한다`() {
        val viewModel = viewModelWithPendingSave()

        assertFalse(viewModel.onSaveCompleted(request.copy(id = "other-alarm")))
        assertTrue(viewModel.uiState.isSaveInProgress)

        assertTrue(viewModel.onSaveCompleted(request))
        assertFalse(viewModel.uiState.isSaveInProgress)
    }

    @Test
    fun `현재 요청의 저장 실패를 받으면 진행 상태를 해제하고 오류를 제공한다`() {
        val viewModel = viewModelWithPendingSave()

        viewModel.onSaveError(request, "알람 저장 실패")

        assertFalse(viewModel.uiState.isSaveInProgress)
        assertEquals("알람 저장 실패", viewModel.uiState.errorMessage)
    }

    private fun viewModelWithPendingSave() = configuredViewModel().apply {
        requestSave()
    }

    private fun configuredViewModel() =
        AlarmSetupViewModel(createAlarmId = { "alarm-1" }).apply {
            startEditing(request)
        }

    private fun locationState(
        services: MissionLocationServicesState = MissionLocationServicesState.ENABLED,
        authorization: MissionLocationAuthorizationState =
            MissionLocationAuthorizationState.ALWAYS,
        accuracy: MissionLocationAccuracyState = MissionLocationAccuracyState.FULL,
        alwaysAuthorizationRequestFailed: Boolean = false,
    ) = MissionLocationState(
        services = services,
        authorization = authorization,
        accuracy = accuracy,
        alwaysAuthorizationRequestFailed = alwaysAuthorizationRequestFailed,
    )

    private companion object {
        val destination = DestinationSelection(
            name = "회사",
            address = "서울특별시 강남구 테헤란로 1",
            latitude = 37.4979,
            longitude = 127.0276,
        )
        val alarmSound = AlarmSoundSelection(
            name = "파도",
            uri = "content://alarm/wave",
        )
        val savedRequest = AlarmScheduleRequest(
            id = "alarm-1",
            time = "08:30",
            selectedDays = listOf("월", "수", "금"),
            repeatEnabled = true,
            limitMinutes = 17,
            destinationName = destination.name,
            destinationAddress = destination.address,
            destinationLatitude = destination.latitude,
            destinationLongitude = destination.longitude,
            alarmSoundName = alarmSound.name,
            alarmSoundUri = alarmSound.uri,
        )
        val request = AlarmScheduleRequest(
            id = "alarm-1",
            time = "07:30",
            selectedDays = listOf("월", "화", "수", "목", "금"),
            repeatEnabled = true,
            limitMinutes = 13,
            destinationName = "회사",
            destinationAddress = "서울특별시 강남구 테헤란로 1",
            destinationLatitude = 37.4979,
            destinationLongitude = 127.0276,
            alarmSoundName = "기본 알람음",
            alarmSoundUri = null,
        )
    }
}
