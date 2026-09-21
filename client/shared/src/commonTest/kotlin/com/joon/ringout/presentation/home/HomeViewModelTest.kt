package com.joon.ringout.presentation.home

import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.presentation.home.model.HomeAlarm
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeViewModelTest {
    @Test
    fun `저장된 알람을 관찰하면 홈 상태에 반영한다`() = runTest {
        val viewModel = HomeViewModel()

        viewModel.observeAlarms(
            flowOf(listOf(SavedAlarmSchedule(request = request, enabled = true))),
        )

        assertFalse(viewModel.uiState.isLoading)
        assertEquals(
            listOf(
                HomeAlarm(
                    id = request.id,
                    time = request.time,
                    days = "평일",
                    destination = request.destinationName,
                    timeLimitMinutes = request.limitMinutes,
                    isEnabled = true,
                    targetAddress = request.destinationAddress,
                    targetLatitude = request.destinationLatitude,
                    targetLongitude = request.destinationLongitude,
                    targetDistanceKm = request.targetDistanceKm,
                    alarmSoundName = request.alarmSoundName,
                    alarmSoundUri = request.alarmSoundUri,
                    selectedDays = request.selectedDays,
                    repeatEnabled = request.repeatEnabled,
                ),
            ),
            viewModel.uiState.alarms,
        )
    }

    @Test
    fun `알람 활성화 변경을 요청하면 실행 명령을 반환한다`() {
        val viewModel = HomeViewModel()

        val command = viewModel.onAlarmEnabledChange(
            alarmId = "alarm-1",
            enabled = false,
        )

        assertEquals(
            HomeViewModel.Command.SetAlarmEnabled(
                alarmId = "alarm-1",
                enabled = false,
            ),
            command,
        )
    }

    @Test
    fun `삭제를 요청하면 알람을 유지하고 확인 대상을 저장한다`() = runTest {
        val viewModel = HomeViewModel()
        viewModel.observeAlarms(flowOf(listOf(SavedAlarmSchedule(request, true))))

        viewModel.onAlarmDelete(request.id)

        assertEquals(request.id, viewModel.uiState.pendingDeleteAlarmId)
        assertEquals(1, viewModel.uiState.alarms.size)
    }

    @Test
    fun `삭제 확인을 취소하면 알람을 유지하고 삭제 명령을 만들지 않는다`() = runTest {
        val viewModel = HomeViewModel()
        viewModel.observeAlarms(flowOf(listOf(SavedAlarmSchedule(request, true))))
        viewModel.onAlarmDelete(request.id)

        viewModel.dismissAlarmDelete()

        assertNull(viewModel.uiState.pendingDeleteAlarmId)
        assertNull(viewModel.confirmAlarmDelete())
        assertEquals(1, viewModel.uiState.alarms.size)
    }

    @Test
    fun `삭제를 확정하면 선택한 알람의 삭제 명령을 한 번만 반환한다`() = runTest {
        val viewModel = HomeViewModel()
        viewModel.observeAlarms(flowOf(listOf(
            SavedAlarmSchedule(request, true),
            SavedAlarmSchedule(request.copy(id = "alarm-2"), true),
        )))
        viewModel.onAlarmDelete("alarm-2")

        assertEquals(HomeViewModel.Command.DeleteAlarm("alarm-2"), viewModel.confirmAlarmDelete())
        assertNull(viewModel.uiState.pendingDeleteAlarmId)
        assertNull(viewModel.confirmAlarmDelete())
    }

    @Test
    fun `삭제 확인 중 대상 알람이 사라지면 확인을 닫고 삭제 명령을 만들지 않는다`() = runTest {
        val viewModel = HomeViewModel()
        viewModel.observeAlarms(flowOf(listOf(SavedAlarmSchedule(request, true))))
        viewModel.onAlarmDelete(request.id)

        viewModel.observeAlarms(flowOf(emptyList()))

        assertNull(viewModel.uiState.pendingDeleteAlarmId)
        assertNull(viewModel.confirmAlarmDelete())
    }

    @Test
    fun `존재하지 않는 알람은 삭제 확인을 열지 않는다`() {
        val viewModel = HomeViewModel()
        viewModel.onAlarmDelete("missing")
        assertNull(viewModel.uiState.pendingDeleteAlarmId)
        assertNull(viewModel.confirmAlarmDelete())
    }

    @Test
    fun `알람 처리 오류를 받으면 홈 오류 상태에 반영하고 해제한다`() {
        val viewModel = HomeViewModel()

        viewModel.showError("알람을 삭제하지 못했습니다.")

        assertEquals("알람을 삭제하지 못했습니다.", viewModel.uiState.errorMessage)

        viewModel.clearError()

        assertNull(viewModel.uiState.errorMessage)
    }

    @Test
    fun `알람 관찰에 실패하면 로딩을 종료하고 오류 메시지를 제공한다`() = runTest {
        val viewModel = HomeViewModel()

        viewModel.observeAlarms(
            flow { throw IllegalStateException("저장된 알람 조회 실패") },
        )

        assertFalse(viewModel.uiState.isLoading)
        assertTrue(viewModel.uiState.alarms.isEmpty())
        assertEquals("저장된 알람 조회 실패", viewModel.uiState.errorMessage)
    }

    @Test
    fun `홈 알람을 선택하면 저장 요청으로 복원한다`() = runTest {
        val viewModel = HomeViewModel()
        viewModel.observeAlarms(
            flowOf(listOf(SavedAlarmSchedule(request = request, enabled = true))),
        )

        assertEquals(request, viewModel.alarmScheduleRequest(alarmId = request.id))
        assertNull(viewModel.alarmScheduleRequest(alarmId = "missing-alarm"))
    }

    private companion object {
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
            targetDistanceKm = 0.8,
            alarmSoundName = "기본 알람음",
            alarmSoundUri = null,
        )
    }
}
