package com.joon.ringout.presentation.alarmsetup

import com.joon.ringout.alarm.AlarmScheduleRequest
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmSetupCoordinatorTest {
    @Test
    fun `알람 설정 명령을 대응하는 콜백으로 전달하고 빈 명령은 무시한다`() {
        val calls = mutableListOf<Any>()
        val request = AlarmScheduleRequest(
            id = "alarm-1",
            time = "07:30",
            selectedDays = listOf("월", "화", "수", "목", "금"),
            repeatEnabled = true,
            limitMinutes = 10,
            destinationName = "회사",
            destinationAddress = "서울특별시 강남구 테헤란로 1",
            destinationLatitude = 37.4979,
            destinationLongitude = 127.0276,
            alarmSoundName = "기본 알람음",
            alarmSoundUri = null,
        )
        val executor = AlarmSetupCommandExecutor(
            scheduleAlarm = { scheduledRequest -> calls += scheduledRequest },
            requestWhenInUseLocation = { calls += "requestWhenInUseLocation" },
            requestAlwaysLocation = { calls += "requestAlwaysLocation" },
            confirmAlwaysLocationResult = { calls += "confirmAlwaysLocationResult" },
            requestTemporaryFullAccuracy = { calls += "requestTemporaryFullAccuracy" },
        )

        executor.execute(AlarmSetupViewModel.Command.ScheduleAlarm(request))
        executor.execute(AlarmSetupViewModel.Command.RequestWhenInUseLocation)
        executor.execute(AlarmSetupViewModel.Command.RequestAlwaysLocation)
        executor.execute(AlarmSetupViewModel.Command.ConfirmAlwaysLocationResult)
        executor.execute(AlarmSetupViewModel.Command.RequestTemporaryFullAccuracy)
        executor.execute(null)

        assertEquals(
            listOf(
                request,
                "requestWhenInUseLocation",
                "requestAlwaysLocation",
                "confirmAlwaysLocationResult",
                "requestTemporaryFullAccuracy",
            ),
            calls,
        )
    }
}
