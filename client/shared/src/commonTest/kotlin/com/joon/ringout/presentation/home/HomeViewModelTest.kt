package com.joon.ringout.presentation.home

import com.joon.ringout.alarm.AlarmScheduleRequest
import com.joon.ringout.alarm.SavedAlarmSchedule
import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.home.model.HomeAlarm
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `즐겨찾기 삭제는 같은 목적지를 사용하는 여러 알람의 표시만 주소로 바꾸고 예약 원본은 유지한다`() = runTest {
        val favorite = SavedDestination(1, request.destinationName, request.destinationAddress,
            request.destinationLatitude, request.destinationLongitude)
        val other = favorite.copy(id = 2, name = "집", latitude = 37.56)
        val favorites = MutableStateFlow(listOf(favorite, other))
        val requests = listOf(request, request.copy(id = "alarm-2"), request.copy(id = "alarm-3",
            destinationName = other.name, destinationLatitude = other.latitude))
        val alarms = MutableStateFlow(requests.map { SavedAlarmSchedule(it, true) })
        val vm = HomeViewModel(favorites)
        backgroundScope.launch { vm.observeAlarms(alarms) }
        runCurrent()
        assertEquals(listOf("회사", "회사", "집"), vm.uiState.alarms.map { it.destinationDisplayName })
        favorites.value = listOf(other)
        runCurrent()
        assertEquals(listOf(request.destinationAddress, request.destinationAddress, "집"), vm.uiState.alarms.map { it.destinationDisplayName })
        requests.forEach { assertEquals(it, vm.alarmScheduleRequest(it.id)) }
        assertEquals(requests.map { SavedAlarmSchedule(it, true) }, alarms.value)
        assertTrue(vm.uiState.alarms.all { it.isEnabled })
    }

    @Test
    fun `삭제 후 새로운 홈 모델로 진입해도 주소를 표시하고 편집 요청에는 원래 목적지를 전달한다`() = runTest {
        val vm = HomeViewModel(flowOf(emptyList()))
        vm.observeAlarms(flowOf(listOf(SavedAlarmSchedule(request, true))))
        assertEquals(request.destinationAddress, vm.uiState.alarms.single().destinationDisplayName)
        assertEquals(request, vm.alarmScheduleRequest(request.id))
    }

    @Test
    fun `즐겨찾기 조회가 끝날 때까지 알람 목록을 표시하며 별명을 유지한다`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val vm = HomeViewModel(flow {
            gate.await()
            emit(emptyList())
        })
        backgroundScope.launch { vm.observeAlarms(flowOf(listOf(SavedAlarmSchedule(request, true)))) }
        runCurrent()
        assertFalse(vm.uiState.isLoading)
        assertEquals(request.destinationName, vm.uiState.alarms.single().destinationDisplayName)
        gate.complete(Unit)
        runCurrent()
        assertEquals(request.destinationAddress, vm.uiState.alarms.single().destinationDisplayName)
    }

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

    @Test
    fun `같은 ID의 수정 저장 Flow가 오면 홈 카드 전체와 활성 상태를 최신 값으로 교체한다`() = runTest {
        val viewModel = HomeViewModel()
        val savedAlarms = MutableStateFlow(
            listOf(SavedAlarmSchedule(request = request, enabled = false)),
        )
        backgroundScope.launch { viewModel.observeAlarms(savedAlarms) }
        runCurrent()
        val edited = request.copy(
            time = "08:40",
            selectedDays = listOf("화", "목"),
            repeatEnabled = true,
            limitMinutes = 20,
            destinationName = "집",
            destinationAddress = "서울특별시 중구 세종대로 110",
            destinationLatitude = 37.5665,
            destinationLongitude = 126.978,
            targetDistanceKm = 1.5,
            alarmSoundName = "벨",
            alarmSoundUri = "content://ringout/alarm/bell",
        )

        savedAlarms.value = listOf(SavedAlarmSchedule(request = edited, enabled = true))
        runCurrent()

        assertEquals(
            HomeAlarm(
                id = edited.id,
                time = "08:40",
                days = "화 목",
                destination = "집",
                timeLimitMinutes = 20,
                isEnabled = true,
                targetAddress = edited.destinationAddress,
                targetLatitude = edited.destinationLatitude,
                targetLongitude = edited.destinationLongitude,
                targetDistanceKm = 1.5,
                alarmSoundName = "벨",
                alarmSoundUri = "content://ringout/alarm/bell",
                selectedDays = listOf("화", "목"),
                repeatEnabled = true,
            ),
            viewModel.uiState.alarms.single(),
        )
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
