package com.joon.ringout.presentation.onboarding

import com.joon.ringout.analytics.AnalyticsEventName
import com.joon.ringout.analytics.AnalyticsParameterName
import com.joon.ringout.analytics.AnalyticsParameterValue
import com.joon.ringout.analytics.OnboardingAnalyticsFixture
import com.joon.ringout.presentation.alarmsetup.AlarmSetupUiState
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.destination.DestinationSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingAnalyticsFlowTest {
    private val alarm = AlarmSetupUiState(
        destination = DestinationSelection("집", "주소", 37.5, 127.0),
    )

    @Test
    fun `뒤로 가기와 지도 복귀를 포함한 실제 단계 진입만 기록한다`() {
        val f = OnboardingAnalyticsFixture()
        val vm = OnboardingViewModel(analytics = f.recorder)
        repeat(3) { vm.onStepVisible() }
        vm.requestNext(alarm, 0)
        vm.onStepVisible()
        vm.back()
        vm.onStepVisible()
        vm.requestNext(alarm, 0)
        vm.onStepVisible()
        vm.requestNext(alarm, 0)
        vm.onStepVisible()
        vm.openDestination()
        vm.onStepVisible() // A map is not another onboarding stage.
        vm.back()
        vm.onStepVisible()
        assertEquals(1, f.events.count { it.name == AnalyticsEventName.TutorialBegin })
        assertEquals(
            listOf("time", "weekdays", "time", "weekdays", "destination", "destination"),
            f.events.filter { it.name == AnalyticsEventName.OnboardingStepViewed }.map {
                (it.parameters.getValue(AnalyticsParameterName.StepName) as AnalyticsParameterValue.Text).value
            },
        )
    }

    @Test
    fun `앱을 재시작하면 단계 진입을 새로 기록하고 온보딩 시작은 중복 기록하지 않는다`() {
        val f = OnboardingAnalyticsFixture()
        repeat(2) { OnboardingViewModel(analytics = f.recorder).onStepVisible() }
        assertEquals(1, f.events.count { it.name == AnalyticsEventName.TutorialBegin })
        assertEquals(2, f.events.count { it.name == AnalyticsEventName.OnboardingStepViewed })
    }

    @Test
    fun `수락된 제출과 재시도는 기록하고 알람 저장만으로 온보딩 완료를 기록하지 않는다`() {
        for (sound in listOf(false, true)) {
            val f = OnboardingAnalyticsFixture()
            val vm = OnboardingViewModel(sound, f.recorder)
            val editor = AlarmSetupViewModel { "first" }
            editor.startCreating("06:20")
            editor.updateDestination(requireNotNull(alarm.destination))
            repeat(vm.uiState.steps.size - 1) { vm.requestNext(editor.uiState, 0) }
            assertEquals(OnboardingAdvance.SaveAlarm, vm.requestNext(editor.uiState, 0))
            if (editor.requestSave()) vm.onSubmitAccepted()
            if (editor.requestSave()) vm.onSubmitAccepted() // in-flight duplicate
            assertEquals(1, f.events.size)
            val request = requireNotNull(editor.uiState.pendingSaveRequest)
            editor.onSaveError(request, "retry")
            if (editor.requestSave()) vm.onSubmitAccepted()
            assertEquals(2, f.events.size)
            assertTrue(editor.onSaveCompleted(requireNotNull(editor.uiState.pendingSaveRequest)))
            assertEquals(OnboardingAdvance.Complete, vm.onAlarmSaved(0))
            assertFalse(f.events.any { it.name == AnalyticsEventName.TutorialComplete })
            // A failed preferences write can be retried without another alarm schedule.
            assertEquals(OnboardingAdvance.Complete, vm.requestNext(editor.uiState, 1))
            vm.onSubmitAccepted()
            assertEquals(3, f.events.size)
            assertTrue(f.events.all { it.name == AnalyticsEventName.OnboardingSubmit })
        }
    }
}
