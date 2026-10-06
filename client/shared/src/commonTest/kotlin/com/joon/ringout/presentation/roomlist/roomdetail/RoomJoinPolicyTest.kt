package com.joon.ringout.presentation.roomlist.roomdetail

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomJoinPolicyTest {
    @Test
    fun `가입 여부와 인증 상태에 따라 한 가지 가입 동작만 선택한다`() {
        assertEquals(
            RoomJoinAction.Join,
            roomJoinAction(previewRoom, AuthSessionState.Authenticated),
        )
        assertEquals(
            RoomJoinAction.RequestLogin,
            roomJoinAction(previewRoom, AuthSessionState.Unauthenticated),
        )
        assertEquals(
            RoomJoinAction.Disabled,
            roomJoinAction(previewRoom, AuthSessionState.Restoring),
        )
        assertEquals(
            RoomJoinAction.Disabled,
            roomJoinAction(previewRoom, AuthSessionState.ReauthenticationRequired),
        )
        assertEquals(
            RoomJoinAction.Disabled,
            roomJoinAction(previewRoom.copy(isJoined = true), AuthSessionState.Authenticated),
        )
    }

    private companion object {
        val previewRoom = RoomUiModel(
            id = "room-1",
            name = "아침 러닝 모임",
            description = "같이 달려요.",
            createdAt = "2026-09-15T09:00:00",
            activityDays = listOf("월", "수", "금"),
            activityTimeText = "오전 6:00",
            participantCount = 6,
            isJoined = false,
        )
    }
}
