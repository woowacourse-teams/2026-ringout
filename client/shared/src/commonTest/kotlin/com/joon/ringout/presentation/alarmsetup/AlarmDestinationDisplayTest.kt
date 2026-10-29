package com.joon.ringout.presentation.alarmsetup

import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.toDestinationSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmDestinationDisplayTest {
    private val destination = SavedDestination(1, "헬스장", 37.5, 127.0)

    @Test
    fun `선택한 목적지 라벨은 저장 목적지 목록과 독립적으로 알람 요청에 복사된다`() {
        val editor = AlarmSetupViewModel()
        editor.startCreating("07:00")
        editor.updateDestination(destination.toDestinationSelection())
        val copiedLabel = editor.uiState.destinationDisplayName

        assertEquals("헬스장", copiedLabel)
        assertTrue(editor.uiState.canSave)
        assertEquals(copiedLabel, editor.createScheduleRequest()?.destinationLabel)
        assertEquals(destination.latitude, editor.createScheduleRequest()?.destinationLatitude)
        assertEquals(destination.longitude, editor.createScheduleRequest()?.destinationLongitude)
    }

    @Test
    fun `목적지를 선택하지 않았거나 목록 조회 전이면 기존 표시 상태를 유지한다`() {
        assertEquals("", AlarmSetupUiState().destinationDisplayName)
        assertEquals("헬스장", AlarmSetupUiState(destination = destination.toDestinationSelection()).destinationDisplayName)
    }
}
