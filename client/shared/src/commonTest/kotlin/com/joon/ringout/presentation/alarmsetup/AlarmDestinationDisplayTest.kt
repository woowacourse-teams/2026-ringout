package com.joon.ringout.presentation.alarmsetup

import com.joon.ringout.domain.destination.SavedDestination
import com.joon.ringout.presentation.destination.toDestinationSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AlarmDestinationDisplayTest {
    private val destination = SavedDestination(1, "헬스장", "서울 서초구 반포동 748-10", 37.5, 127.0)

    @Test
    fun `알람 초안의 선택 정보는 유지하고 즐겨찾기 삭제 후 표시 이름만 주소로 바꾼다`() {
        val editor = AlarmSetupViewModel()
        editor.startCreating("07:00")
        editor.updateDestination(destination.toDestinationSelection())
        val draft = editor.uiState
        assertEquals("헬스장", draft.copy(savedDestinations = listOf(destination)).destinationDisplayName)
        val afterDeletion = draft.copy(savedDestinations = emptyList())
        assertEquals(destination.address, afterDeletion.destinationDisplayName)
        assertEquals(draft.destination, afterDeletion.destination)
        assertTrue(afterDeletion.canSave)
        assertEquals(draft, editor.uiState)
        assertEquals(destination.name, editor.createScheduleRequest()?.destinationName)
        assertEquals(destination.latitude, editor.createScheduleRequest()?.destinationLatitude)
    }

    @Test
    fun `목적지를 선택하지 않았거나 목록 조회 전이면 기존 표시 상태를 유지한다`() {
        assertEquals("", AlarmSetupUiState(savedDestinations = emptyList()).destinationDisplayName)
        assertEquals("헬스장", AlarmSetupUiState(destination = destination.toDestinationSelection()).destinationDisplayName)
    }
}
