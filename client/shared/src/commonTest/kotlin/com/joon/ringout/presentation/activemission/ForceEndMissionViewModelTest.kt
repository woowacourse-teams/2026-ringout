package com.joon.ringout.presentation.activemission

import androidx.lifecycle.SavedStateHandle
import com.joon.ringout.alarm.ForceEndMissionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ForceEndMissionViewModelTest {
    @Test
    fun `종료 확정 전에는 완료 안내를 표시하지 않고 중복 요청과 확인으로 작업을 중단하지 않는다`() = runTest {
        val vm = ForceEndMissionViewModel(SavedStateHandle(), backgroundScope)
        val result = CompletableDeferred<ForceEndMissionResult>()
        var calls = 0
        val stop: suspend (String) -> ForceEndMissionResult = { calls++; result.await() }
        vm.request("mission", stop)
        runCurrent()
        vm.request("mission", stop)
        vm.resumePending(stop)
        vm.dismiss()
        assertEquals(1, calls)
        assertEquals(ForceEndPhase.Processing, vm.uiState.value.phase)

        result.complete(ForceEndMissionResult.Completed)
        runCurrent()
        assertEquals(ForceEndPhase.Completed, vm.uiState.value.phase)
        vm.request("mission", stop)
        vm.dismiss()
        runCurrent()
        assertEquals(1, calls)
        assertEquals(ForceEndPhase.Idle, vm.uiState.value.phase)
    }

    @Test
    fun `종료 실패는 재시도할 수 있고 재시도 성공 후에만 완료 안내가 표시된다`() = runTest {
        val vm = ForceEndMissionViewModel(SavedStateHandle(), backgroundScope)
        vm.request("mission") { error("저장 실패") }
        runCurrent()
        assertEquals(ForceEndPhase.Failed, vm.uiState.value.phase)
        vm.retry { id ->
            assertEquals("mission", id)
            ForceEndMissionResult.Completed
        }
        runCurrent()
        assertEquals(ForceEndPhase.Completed, vm.uiState.value.phase)
    }

    @Test
    fun `도착이나 만료로 이미 종료된 미션은 강제 종료 완료로 안내하지 않는다`() = runTest {
        val vm = ForceEndMissionViewModel(SavedStateHandle(), backgroundScope)
        vm.request("old") { ForceEndMissionResult.NoLongerActive }
        runCurrent()
        assertEquals(ForceEndPhase.Idle, vm.uiState.value.phase)
        vm.request("new") { ForceEndMissionResult.Completed }
        runCurrent()
        assertEquals("new", vm.uiState.value.occurrenceId)
        assertEquals(ForceEndPhase.Completed, vm.uiState.value.phase)
    }

    @Test
    fun `완료 안내를 복원하거나 확인해도 종료를 다시 실행하지 않는다`() = runTest {
        val saved = SavedStateHandle()
        val original = ForceEndMissionViewModel(saved, backgroundScope)
        original.request("mission") { ForceEndMissionResult.Completed }
        runCurrent()
        val restored = ForceEndMissionViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }), backgroundScope)
        var calls = 0
        restored.resumePending { calls++; ForceEndMissionResult.Completed }
        assertEquals(ForceEndPhase.Completed, restored.uiState.value.phase)
        restored.dismiss()
        runCurrent()
        assertEquals(0, calls)
        assertEquals(ForceEndPhase.Idle, restored.uiState.value.phase)
    }

    @Test
    fun `저장된 처리 중 요청은 새 뷰모델에서 같은 회차로 한 번만 복구한다`() = runTest {
        val saved = SavedStateHandle()
        val original = ForceEndMissionViewModel(saved, backgroundScope)
        original.request("mission") { CompletableDeferred<ForceEndMissionResult>().await() }
        runCurrent()
        val restored = ForceEndMissionViewModel(SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) }), backgroundScope)
        var calls = 0
        val stop: suspend (String) -> ForceEndMissionResult = {
            assertEquals("mission", it)
            calls++
            ForceEndMissionResult.Completed
        }
        restored.resumePending(stop)
        restored.resumePending(stop)
        runCurrent()
        assertEquals(1, calls)
        assertEquals(ForceEndPhase.Completed, restored.uiState.value.phase)
    }
}
