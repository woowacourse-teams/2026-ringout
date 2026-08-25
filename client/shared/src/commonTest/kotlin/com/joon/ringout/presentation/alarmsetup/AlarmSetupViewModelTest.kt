package com.joon.ringout.presentation.alarmsetup

import com.joon.ringout.alarm.AlarmScheduleRequest
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
    }
}
