package com.joon.ringout.presentation.alarmguide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmCreationGuideViewModelTest {
    @Test
    fun `가이드 대기 상태인 사용자가 홈에 진입하면 알람 추가 안내를 시작한다`() {
        val viewModel = guideOnHome()

        assertEquals(AlarmCreationGuideStep.AddAlarm, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.isFinishing)
    }

    @Test
    fun `가이드 대기 상태가 아닌 기존 사용자는 홈에서도 안내를 시작하지 않는다`() {
        val viewModel = AlarmCreationGuideViewModel()

        viewModel.updateContext(isPending = false, surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideUiState(), viewModel.uiState.value)
        assertFalse(viewModel.onNext(AlarmCreationGuideStep.AddAlarm))
        assertFalse(viewModel.finish())
    }

    @Test
    fun `홈 이외의 화면에 먼저 진입하면 홈을 방문할 때까지 안내를 보류한다`() {
        val viewModel = AlarmCreationGuideViewModel()

        listOf(AlarmCreationGuideSurface.Other, AlarmCreationGuideSurface.Editor).forEach { surface ->
            viewModel.updateContext(surface = surface)
            assertNull(viewModel.uiState.value.step)
        }
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideStep.AddAlarm, viewModel.uiState.value.step)
    }

    @Test
    fun `안내를 표시할 수 없는 홈에서는 시작을 보류하고 표시 가능해지면 시작한다`() {
        val viewModel = AlarmCreationGuideViewModel()

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home, canPresent = false)
        assertNull(viewModel.uiState.value.step)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home, canPresent = true)
        assertEquals(AlarmCreationGuideStep.AddAlarm, viewModel.uiState.value.step)
    }

    @Test
    fun `홈 안내를 넘긴 뒤 작성 화면이 열리기 전 반복 입력으로 시간 안내를 건너뛰지 않는다`() {
        val viewModel = guideOnHome()

        assertTrue(viewModel.onNext(AlarmCreationGuideStep.AddAlarm))
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home)
        repeat(3) {
            assertFalse(viewModel.onNext(AlarmCreationGuideStep.AddAlarm))
            assertFalse(viewModel.onNext(AlarmCreationGuideStep.Time))
        }
        assertEquals(AlarmCreationGuideStep.Time, viewModel.uiState.value.step)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor)
        assertTrue(viewModel.onNext(AlarmCreationGuideStep.Time))
        assertEquals(AlarmCreationGuideStep.Weekdays, viewModel.uiState.value.step)
    }

    @Test
    fun `현재 안내와 다른 단계의 오래된 터치로 안내를 넘기지 않는다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.Destination)
        val currentState = viewModel.uiState.value

        AlarmCreationGuideStep.entries.filter { it != AlarmCreationGuideStep.Destination }.forEach { step ->
            assertFalse(viewModel.onNext(step))
            assertEquals(currentState, viewModel.uiState.value)
        }
    }

    @Test
    fun `같은 안내에서 전달된 연속 터치는 한 단계만 진행한다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.Time)

        assertTrue(viewModel.onNext(AlarmCreationGuideStep.Time))
        repeat(3) { assertFalse(viewModel.onNext(AlarmCreationGuideStep.Time)) }

        assertEquals(AlarmCreationGuideStep.Weekdays, viewModel.uiState.value.step)
    }

    @Test
    fun `목적지나 알람음 선택 없이 화면 터치만으로 모든 안내를 순서대로 진행한다`() {
        val viewModel = guideOnHome()
        assertTrue(viewModel.onNext(AlarmCreationGuideStep.AddAlarm))
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor)

        val editorSteps = AlarmCreationGuideStep.entries.drop(1)
        editorSteps.zipWithNext().forEach { (step, nextStep) ->
            assertEquals(step, viewModel.uiState.value.step)
            assertTrue(viewModel.onNext(step))
            assertEquals(nextStep, viewModel.uiState.value.step)
            assertFalse(viewModel.uiState.value.isFinishing)
        }
    }

    @Test
    fun `목적지 설정이나 알람 생성 결과 없이 마지막 안내를 누르면 가이드 종료를 요청한다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.CreateAlarm)

        assertTrue(viewModel.onNext(AlarmCreationGuideStep.CreateAlarm))

        assertEquals(AlarmCreationGuideStep.CreateAlarm, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.isFinishing)
        assertFalse(viewModel.onNext(AlarmCreationGuideStep.CreateAlarm))
        assertFalse(viewModel.finish())
    }

    @Test
    fun `우선 화면이 안내를 가리면 현재 단계를 유지하고 지연 입력을 무시한다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.Destination)
        val beforeInterruption = viewModel.uiState.value

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Other, canPresent = false)
        assertFalse(viewModel.onNext(AlarmCreationGuideStep.Destination))
        assertFalse(viewModel.finish())
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, canPresent = false)
        assertFalse(viewModel.onNext(AlarmCreationGuideStep.Destination))
        assertEquals(beforeInterruption, viewModel.uiState.value)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, canPresent = true)
        assertEquals(beforeInterruption, viewModel.uiState.value)
        assertTrue(viewModel.onNext(AlarmCreationGuideStep.Destination))
        assertEquals(AlarmCreationGuideStep.Interval, viewModel.uiState.value.step)
    }

    @Test
    fun `가이드 대상이 아닌 화면에서는 표시 가능 상태여도 진행하거나 종료하지 않는다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.Sound)
        val beforeInterruption = viewModel.uiState.value

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Other)

        assertFalse(viewModel.onNext(AlarmCreationGuideStep.Sound))
        assertFalse(viewModel.finish())
        assertEquals(beforeInterruption, viewModel.uiState.value)
    }

    @Test
    fun `작성 화면 진입 전 우선 화면으로 안내를 잠시 가려도 시간 안내를 유지한다`() {
        val viewModel = guideOnHome()
        viewModel.onNext(AlarmCreationGuideStep.AddAlarm)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home, canPresent = false)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home, canPresent = true)

        assertEquals(AlarmCreationGuideStep.Time, viewModel.uiState.value.step)
        assertFalse(viewModel.onNext(AlarmCreationGuideStep.Time))
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor)
        assertTrue(viewModel.onNext(AlarmCreationGuideStep.Time))
    }

    @Test
    fun `어느 단계에서든 건너뛰면 중복 종료와 단계 진행을 잠근다`() {
        AlarmCreationGuideStep.entries.forEach { step ->
            val viewModel = guideAt(step)

            assertTrue(viewModel.finish())
            assertTrue(viewModel.uiState.value.isFinishing)
            assertEquals(step, viewModel.uiState.value.step)
            assertFalse(viewModel.finish())
            assertFalse(viewModel.onNext(step))
        }
    }

    @Test
    fun `종료 저장을 기다리는 동안 화면이 바뀌어도 재시도를 위한 종료 상태를 유지한다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.CreateAlarm)
        viewModel.onNext(AlarmCreationGuideStep.CreateAlarm)
        val finishingState = viewModel.uiState.value

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Other, canPresent = false)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home)

        assertEquals(finishingState, viewModel.uiState.value)
        assertFalse(viewModel.onNext(AlarmCreationGuideStep.AddAlarm))
        assertFalse(viewModel.finish())
    }

    @Test
    fun `가이드 완료가 저장되면 표시할 수 없는 상태에서도 진행 정보를 비운다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.Sound)
        viewModel.finish()

        viewModel.updateContext(isPending = false, surface = AlarmCreationGuideSurface.Editor, canPresent = false)

        assertEquals(AlarmCreationGuideUiState(), viewModel.uiState.value)
        assertFalse(viewModel.onNext(AlarmCreationGuideStep.Sound))
    }

    @Test
    fun `알람 편집을 버리고 홈으로 돌아오면 알람 추가 안내부터 다시 시작한다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.Destination)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideStep.AddAlarm, viewModel.uiState.value.step)
        assertTrue(viewModel.onNext(AlarmCreationGuideStep.AddAlarm))
        assertEquals(AlarmCreationGuideStep.Time, viewModel.uiState.value.step)
    }

    @Test
    fun `안내가 가려진 동안 작성 화면에서 홈으로 돌아와도 재개 시 알람 추가 안내를 표시한다`() {
        val viewModel = guideAt(AlarmCreationGuideStep.Destination)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home, canPresent = false)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home, canPresent = true)

        assertEquals(AlarmCreationGuideStep.AddAlarm, viewModel.uiState.value.step)
        assertTrue(viewModel.onNext(AlarmCreationGuideStep.AddAlarm))
    }

    @Test
    fun `프로세스가 재시작되어도 저장된 가이드 대기 상태로 홈에서 다시 시작한다`() {
        val restoredViewModel = AlarmCreationGuideViewModel()

        restoredViewModel.updateContext(surface = AlarmCreationGuideSurface.Editor)
        assertNull(restoredViewModel.uiState.value.step)
        restoredViewModel.updateContext(surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideStep.AddAlarm, restoredViewModel.uiState.value.step)
    }
}

private fun guideOnHome() = AlarmCreationGuideViewModel().apply {
    updateContext(surface = AlarmCreationGuideSurface.Home)
}

private fun guideAt(target: AlarmCreationGuideStep) = guideOnHome().apply {
    while (uiState.value.step != target) {
        val step = requireNotNull(uiState.value.step)
        check(onNext(step))
        if (step == AlarmCreationGuideStep.AddAlarm) updateContext(surface = AlarmCreationGuideSurface.Editor)
    }
}

private fun AlarmCreationGuideViewModel.updateContext(
    surface: AlarmCreationGuideSurface,
    isPending: Boolean = true,
    canPresent: Boolean = true,
) = onContextChanged(isPending = isPending, surface = surface, canPresent = canPresent)
