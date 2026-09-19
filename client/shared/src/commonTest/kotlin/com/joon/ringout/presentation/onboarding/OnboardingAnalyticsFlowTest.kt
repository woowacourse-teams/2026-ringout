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
    fun recordsOnlyActualEntriesIncludingBackAndMapReturn() {
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
    fun appRestartRecordsNewEntryButNotNewTutorialBegin() {
        val f = OnboardingAnalyticsFixture()
        repeat(2) { OnboardingViewModel(analytics = f.recorder).onStepVisible() }
        assertEquals(1, f.events.count { it.name == AnalyticsEventName.TutorialBegin })
        assertEquals(2, f.events.count { it.name == AnalyticsEventName.OnboardingStepViewed })
    }

    @Test
    fun acceptedSubmissionAndRetryAreMeasuredButSavingDoesNotMeanTutorialComplete() {
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
