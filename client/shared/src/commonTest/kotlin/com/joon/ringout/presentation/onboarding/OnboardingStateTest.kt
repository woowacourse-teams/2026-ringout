package com.joon.ringout.presentation.onboarding

import com.joon.ringout.presentation.alarmsetup.AlarmSetupUiState
import com.joon.ringout.presentation.alarmsetup.AlarmSetupViewModel
import com.joon.ringout.presentation.alarmsetup.AlarmSoundSelection
import com.joon.ringout.presentation.destination.DestinationSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OnboardingStateTest {
    private val destination = DestinationSelection("회사", "서울특별시 중구 세종대로 110", 37.5665, 126.978)
    private val configuredAlarm = AlarmSetupUiState(destination = destination)

    @Test
    fun followsFiveStepsAndRequiresDestination() {
        val flow = OnboardingViewModel()
        assertEquals(OnboardingStep.Time, flow.uiState.step)
        flow.requestNext(AlarmSetupUiState(), 0)
        assertEquals(OnboardingStep.Weekdays, flow.uiState.step)
        flow.requestNext(AlarmSetupUiState(), 0)
        assertEquals(OnboardingStep.Destination, flow.uiState.step)
        assertEquals(OnboardingAdvance.None, flow.requestNext(AlarmSetupUiState(), 0))
        assertEquals(OnboardingStep.Destination, flow.uiState.step)
        flow.requestNext(configuredAlarm, 0)
        assertEquals(OnboardingStep.Interval, flow.uiState.step)
        flow.requestNext(configuredAlarm, 0)
        assertEquals(OnboardingStep.Sound, flow.uiState.step)
        assertEquals(OnboardingAdvance.SaveAlarm, flow.requestNext(configuredAlarm, 0))
        assertFalse(flow.uiState.isAlarmSaved)
    }

    @Test
    fun ignoresDestinationResultsFromClosedOrPreviousPicker() {
        val flow = OnboardingViewModel()
        repeat(2) { flow.requestNext(configuredAlarm, 0) }
        flow.openDestination()
        val firstRequest = flow.uiState.destinationRequestId
        assertEquals(OnboardingAdvance.None, flow.requestNext(configuredAlarm, 0))
        flow.back()
        assertEquals(OnboardingStep.Destination, flow.uiState.step)
        assertFalse(flow.acceptDestination(firstRequest))
        flow.openDestination()
        assertFalse(flow.acceptDestination(firstRequest))
        assertTrue(flow.acceptDestination(flow.uiState.destinationRequestId))
        assertFalse(flow.uiState.isDestinationOpen)
    }

    @Test
    fun retainsInputsAcrossBackNavigationAndSchedulesSelectedValues() {
        val flow = OnboardingViewModel()
        val editor = AlarmSetupViewModel(createAlarmId = { "first-alarm" })
        editor.startCreating("06:20")
        editor.updateHour(8)
        editor.updateMinute(35)
        editor.updateAmPm(false)
        editor.toggleDay("토")
        editor.toggleDay("일")
        editor.updateDestination(destination)
        editor.updateLimitMinutes(5)
        editor.updateAlarmSound(AlarmSoundSelection("Argon", "argon"))
        repeat(4) { flow.requestNext(editor.uiState, 0) }
        flow.back()
        assertEquals(OnboardingStep.Interval, flow.uiState.step)
        flow.requestNext(editor.uiState, 0)
        assertEquals(OnboardingAdvance.SaveAlarm, flow.requestNext(editor.uiState, 0))
        assertTrue(editor.requestSave())
        val request = assertNotNull(editor.uiState.pendingSaveRequest)
        assertEquals("20:35", request.time)
        assertEquals(listOf("월", "화", "수", "목", "금"), request.selectedDays)
        assertEquals("회사", request.destinationName)
        assertEquals(5, request.limitMinutes)
        assertEquals("argon", request.alarmSoundUri)
        assertEquals(OnboardingAdvance.None, flow.requestNext(editor.uiState, 0))
        assertFalse(editor.requestSave())
        editor.onSaveError(request, "저장 실패")
        assertFalse(flow.uiState.isAlarmSaved)
        assertEquals(OnboardingAdvance.SaveAlarm, flow.requestNext(editor.uiState, 0))
        assertTrue(editor.requestSave())
    }

    @Test
    fun completionRetryDoesNotCreateAnotherAlarm() {
        val flow = OnboardingViewModel()
        val editor = AlarmSetupViewModel(createAlarmId = { "first-alarm" })
        editor.startCreating("06:20")
        editor.updateDestination(destination)
        repeat(4) { flow.requestNext(editor.uiState, 0) }
        assertTrue(editor.requestSave())
        val request = assertNotNull(editor.uiState.pendingSaveRequest)
        assertTrue(editor.onSaveCompleted(request))
        assertEquals(OnboardingAdvance.Complete, flow.onAlarmSaved(0))
        assertFalse(editor.onSaveCompleted(request))
        assertEquals(OnboardingAdvance.None, flow.requestNext(editor.uiState, 0))
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(editor.uiState, 1))
        assertEquals(OnboardingAdvance.None, flow.requestNext(editor.uiState, 1))
        flow.back()
        assertEquals(OnboardingStep.Sound, flow.uiState.step)
        assertTrue(flow.uiState.isAlarmSaved)
    }

    @Test
    fun iosHasFourStepsAndSavesFromIntervalWithDefaultSound() {
        val flow = OnboardingViewModel(includesSoundSelection = false)
        val editor = AlarmSetupViewModel(createAlarmId = { "ios-first-alarm" })
        editor.startCreating("06:20")
        editor.updateDestination(destination)
        assertEquals(
            listOf(OnboardingStep.Time, OnboardingStep.Weekdays, OnboardingStep.Destination, OnboardingStep.Interval),
            flow.uiState.steps,
        )
        repeat(3) { flow.requestNext(editor.uiState, 0) }
        assertEquals(3, flow.uiState.currentStepIndex)
        assertTrue(flow.uiState.isLastStep)
        flow.back()
        assertEquals(OnboardingStep.Destination, flow.uiState.step)
        flow.requestNext(editor.uiState, 0)
        assertEquals(OnboardingAdvance.SaveAlarm, flow.requestNext(editor.uiState, 0))
        assertTrue(editor.requestSave())
        val request = assertNotNull(editor.uiState.pendingSaveRequest)
        assertEquals(AlarmSetupUiState().alarmSound.name, request.alarmSoundName)
        assertEquals(null, request.alarmSoundUri)
        assertEquals(OnboardingAdvance.None, flow.requestNext(editor.uiState, 0))
        editor.onSaveError(request, "저장 실패")
        assertEquals(OnboardingAdvance.SaveAlarm, flow.requestNext(editor.uiState, 0))
        assertTrue(editor.requestSave())
        assertTrue(editor.onSaveCompleted(assertNotNull(editor.uiState.pendingSaveRequest)))
        assertEquals(OnboardingAdvance.Complete, flow.onAlarmSaved(0))
        assertEquals(OnboardingAdvance.None, flow.requestNext(editor.uiState, 0))
        assertEquals(OnboardingAdvance.Complete, flow.requestNext(editor.uiState, 1))
        flow.back()
        assertEquals(OnboardingStep.Interval, flow.uiState.step)
    }

    @Test
    fun androidIntervalStillAdvancesToSoundBeforeSaving() {
        val flow = OnboardingViewModel(includesSoundSelection = true)
        repeat(3) { flow.requestNext(configuredAlarm, 0) }
        assertEquals(5, flow.uiState.steps.size)
        assertFalse(flow.uiState.isLastStep)
        assertEquals(OnboardingAdvance.None, flow.requestNext(configuredAlarm, 0))
        assertEquals(OnboardingStep.Sound, flow.uiState.step)
        assertTrue(flow.uiState.isLastStep)
        assertEquals(OnboardingAdvance.SaveAlarm, flow.requestNext(configuredAlarm, 0))
    }

}
