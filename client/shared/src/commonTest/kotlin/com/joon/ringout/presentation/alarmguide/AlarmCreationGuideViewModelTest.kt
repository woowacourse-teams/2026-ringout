package com.joon.ringout.presentation.alarmguide

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlarmCreationGuideViewModelTest {
    @Test
    fun `가이드 대기 상태인 사용자가 홈에 진입하면 알람 추가 안내를 시작한다`() {
        val viewModel = AlarmCreationGuideViewModel()

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideStep.AddAlarm, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
        assertFalse(viewModel.uiState.value.isFinishing)
    }

    @Test
    fun `가이드 대기 상태가 아닌 기존 사용자는 홈에서도 안내를 시작하지 않는다`() {
        val viewModel = AlarmCreationGuideViewModel()

        viewModel.updateContext(isPending = false, surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideUiState(), viewModel.uiState.value)
        assertFalse(viewModel.onTargetPressed(AlarmCreationGuideStep.AddAlarm))
        assertFalse(viewModel.finish())
    }

    @Test
    fun `홈 이외의 화면에 먼저 진입하면 홈을 방문할 때까지 안내를 보류한다`() {
        val viewModel = AlarmCreationGuideViewModel()
        val nonHomeSurfaces = listOf(
            AlarmCreationGuideSurface.Other,
            AlarmCreationGuideSurface.Editor,
            AlarmCreationGuideSurface.Destination,
            AlarmCreationGuideSurface.Sound,
        )

        nonHomeSurfaces.forEach { surface ->
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
    fun `진행 중 안내를 표시할 수 없어도 현재 단계와 선택 상태를 유지한다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)
        val beforeInterruption = viewModel.uiState.value

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Other, canPresent = false)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, canPresent = false)

        assertEquals(beforeInterruption, viewModel.uiState.value)
    }

    @Test
    fun `현재 안내와 다른 버튼을 누르면 단계를 변경하지 않는다`() {
        val viewModel = guideOnHome()
        val initialState = viewModel.uiState.value

        AlarmCreationGuideStep.entries.filter { it != AlarmCreationGuideStep.AddAlarm }.forEach { step ->
            assertFalse(viewModel.onTargetPressed(step))
            assertEquals(initialState, viewModel.uiState.value)
        }
    }

    @Test
    fun `안내 대상 버튼과 선택 완료를 따라 알람 추가부터 생성까지 순서대로 진행한다`() {
        val viewModel = guideOnHome()

        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.AddAlarm))
        assertEquals(AlarmCreationGuideStep.Time, viewModel.uiState.value.step)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor)
        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.Time))
        assertEquals(AlarmCreationGuideStep.Weekdays, viewModel.uiState.value.step)
        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.Weekdays))
        assertEquals(AlarmCreationGuideStep.Destination, viewModel.uiState.value.step)

        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.Destination))
        assertEquals(AlarmCreationGuideSelection.Destination, viewModel.uiState.value.selection)
        assertEquals(AlarmCreationGuideStep.Destination, viewModel.uiState.value.step)
        viewModel.onDestinationSelectionFinished(hasDestination = true)
        assertEquals(AlarmCreationGuideStep.Interval, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)

        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.Interval))
        assertEquals(AlarmCreationGuideStep.Sound, viewModel.uiState.value.step)
        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.Sound))
        assertEquals(AlarmCreationGuideSelection.Sound, viewModel.uiState.value.selection)
        assertEquals(AlarmCreationGuideStep.Sound, viewModel.uiState.value.step)
        viewModel.onSoundSelectionFinished(selected = true)
        assertEquals(AlarmCreationGuideStep.CreateAlarm, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun `목적지를 선택하는 동안 같은 안내 버튼을 다시 눌러도 진행하지 않는다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)
        val selectingState = viewModel.uiState.value

        assertFalse(viewModel.onTargetPressed(AlarmCreationGuideStep.Destination))
        assertFalse(viewModel.onTargetPressed(AlarmCreationGuideStep.Interval))
        assertEquals(selectingState, viewModel.uiState.value)
    }

    @Test
    fun `목적지 선택을 취소하면 목적지 안내에 머물고 다시 선택할 수 있다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Destination)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, hasDestination = false)

        assertEquals(AlarmCreationGuideStep.Destination, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.Destination))
        viewModel.onDestinationSelectionFinished(hasDestination = true)
        assertEquals(AlarmCreationGuideStep.Interval, viewModel.uiState.value.step)
    }

    @Test
    fun `목적지 선택 화면에서 목적지를 정하고 돌아오면 반복 간격 안내로 진행한다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Destination)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, hasDestination = true)

        assertEquals(AlarmCreationGuideStep.Interval, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun `목적지 선택 취소 콜백도 목적지 안내를 다시 표시한다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)

        viewModel.onDestinationSelectionFinished(hasDestination = false)

        assertEquals(AlarmCreationGuideStep.Destination, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun `목적지 관리에서 재개된 목록으로 목적지를 선택해도 반복 간격 안내로 진행한다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Destination)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, hasDestination = false)
        assertEquals(AlarmCreationGuideStep.Destination, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)

        // 관리 화면에서 돌아오면 목록은 안내 버튼을 다시 누르지 않아도 재개된다.
        viewModel.onDestinationSelectionFinished(hasDestination = true)

        assertEquals(AlarmCreationGuideStep.Interval, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun `표시 보류 중 선택 화면에서 취소하고 돌아와도 안내가 재개되면 선택 대기를 해제한다`() {
        val selectionCases = listOf(
            Triple(guideAtDestination(), AlarmCreationGuideStep.Destination, AlarmCreationGuideSurface.Destination),
            Triple(guideAtSound(), AlarmCreationGuideStep.Sound, AlarmCreationGuideSurface.Sound),
        )
        selectionCases.forEach { (viewModel, step, selectionSurface) ->
            viewModel.onTargetPressed(step)
            val beforeInterruption = viewModel.uiState.value
            viewModel.updateContext(surface = selectionSurface, canPresent = false)
            viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, canPresent = false)
            assertEquals(beforeInterruption, viewModel.uiState.value)

            viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, canPresent = true)

            assertEquals(step, viewModel.uiState.value.step)
            assertNull(viewModel.uiState.value.selection)
            assertTrue(viewModel.onTargetPressed(step))
        }
    }

    @Test
    fun `알람음 선택을 취소하고 돌아오면 알람음 안내에 머문다`() {
        val viewModel = guideAtSound()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Sound)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Sound)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, hasDestination = true)

        assertEquals(AlarmCreationGuideStep.Sound, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.Sound))
    }

    @Test
    fun `알람음 선택 취소 콜백도 알람음 안내를 다시 표시한다`() {
        val viewModel = guideAtSound()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Sound)

        viewModel.onSoundSelectionFinished(selected = false)

        assertEquals(AlarmCreationGuideStep.Sound, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun `알람음을 선택한 뒤 편집 화면으로 돌아와도 생성 안내를 유지한다`() {
        val viewModel = guideAtSound()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Sound)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Sound)

        viewModel.onSoundSelectionFinished(selected = true)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Editor, hasDestination = true)

        assertEquals(AlarmCreationGuideStep.CreateAlarm, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
    }

    @Test
    fun `선택 중이 아닌 단계에는 목적지나 알람음 완료 콜백이 영향을 주지 않는다`() {
        val viewModel = guideOnHome()
        val initialState = viewModel.uiState.value

        viewModel.onDestinationSelectionFinished(hasDestination = true)
        viewModel.onSoundSelectionFinished(selected = true)

        assertEquals(initialState, viewModel.uiState.value)
    }

    @Test
    fun `생성 버튼 클릭만으로 종료하지 않고 실제 알람 생성 성공 시에만 종료한다`() {
        val viewModel = guideAtCreateAlarm()

        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.CreateAlarm))
        assertEquals(AlarmCreationGuideStep.CreateAlarm, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.isFinishing)

        assertTrue(viewModel.onAlarmCreated())
        assertTrue(viewModel.uiState.value.isFinishing)
        assertFalse(viewModel.onAlarmCreated())
        assertFalse(viewModel.onTargetPressed(AlarmCreationGuideStep.CreateAlarm))
    }

    @Test
    fun `생성 안내에 도달하기 전 알람 생성 콜백으로 가이드를 종료하지 않는다`() {
        val viewModel = guideAtDestination()
        val beforeCallback = viewModel.uiState.value

        assertFalse(viewModel.onAlarmCreated())
        assertEquals(beforeCallback, viewModel.uiState.value)
    }

    @Test
    fun `건너뛰기를 요청하면 중복 종료와 단계 진행을 잠그고 저장 완료 시 상태를 비운다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)

        assertTrue(viewModel.finish())
        assertTrue(viewModel.uiState.value.isFinishing)
        assertNull(viewModel.uiState.value.selection)
        assertFalse(viewModel.finish())
        assertFalse(viewModel.onTargetPressed(AlarmCreationGuideStep.Destination))
        val finishingState = viewModel.uiState.value
        viewModel.onDestinationSelectionFinished(hasDestination = true)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home)
        assertEquals(finishingState, viewModel.uiState.value)

        viewModel.updateContext(isPending = false, surface = AlarmCreationGuideSurface.Home)
        assertEquals(AlarmCreationGuideUiState(), viewModel.uiState.value)
    }

    @Test
    fun `가이드 저장이 완료되면 표시할 수 없는 상태에서도 진행 정보를 비운다`() {
        val viewModel = guideAtSound()

        viewModel.updateContext(
            isPending = false,
            surface = AlarmCreationGuideSurface.Editor,
            canPresent = false,
        )

        assertEquals(AlarmCreationGuideUiState(), viewModel.uiState.value)
    }

    @Test
    fun `알람 편집을 버리고 홈으로 돌아오면 알람 추가 안내부터 다시 시작한다`() {
        val viewModel = guideAtDestination()
        viewModel.onTargetPressed(AlarmCreationGuideStep.Destination)
        viewModel.updateContext(surface = AlarmCreationGuideSurface.Destination)

        viewModel.updateContext(surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideStep.AddAlarm, viewModel.uiState.value.step)
        assertNull(viewModel.uiState.value.selection)
        assertFalse(viewModel.uiState.value.isFinishing)
        assertTrue(viewModel.onTargetPressed(AlarmCreationGuideStep.AddAlarm))
        assertEquals(AlarmCreationGuideStep.Time, viewModel.uiState.value.step)
    }

    @Test
    fun `프로세스가 재시작되어도 저장된 가이드 대기 상태로 홈에서 다시 시작한다`() {
        val previousViewModel = guideAtCreateAlarm()
        assertEquals(AlarmCreationGuideStep.CreateAlarm, previousViewModel.uiState.value.step)
        val restoredViewModel = AlarmCreationGuideViewModel()

        restoredViewModel.updateContext(surface = AlarmCreationGuideSurface.Editor)
        assertNull(restoredViewModel.uiState.value.step)
        restoredViewModel.updateContext(surface = AlarmCreationGuideSurface.Home)

        assertEquals(AlarmCreationGuideStep.AddAlarm, restoredViewModel.uiState.value.step)
        assertNull(restoredViewModel.uiState.value.selection)
    }
}

private fun guideOnHome() = AlarmCreationGuideViewModel().apply {
    updateContext(surface = AlarmCreationGuideSurface.Home)
}

private fun guideAtDestination() = guideOnHome().apply {
    onTargetPressed(AlarmCreationGuideStep.AddAlarm)
    updateContext(surface = AlarmCreationGuideSurface.Editor)
    onTargetPressed(AlarmCreationGuideStep.Time)
    onTargetPressed(AlarmCreationGuideStep.Weekdays)
}

private fun guideAtSound() = guideAtDestination().apply {
    onTargetPressed(AlarmCreationGuideStep.Destination)
    onDestinationSelectionFinished(hasDestination = true)
    onTargetPressed(AlarmCreationGuideStep.Interval)
}

private fun guideAtCreateAlarm() = guideAtSound().apply {
    onTargetPressed(AlarmCreationGuideStep.Sound)
    onSoundSelectionFinished(selected = true)
}

private fun AlarmCreationGuideViewModel.updateContext(
    surface: AlarmCreationGuideSurface,
    isPending: Boolean = true,
    canPresent: Boolean = true,
    hasDestination: Boolean = false,
) = onContextChanged(
    isPending = isPending,
    surface = surface,
    canPresent = canPresent,
    hasDestination = hasDestination,
)
