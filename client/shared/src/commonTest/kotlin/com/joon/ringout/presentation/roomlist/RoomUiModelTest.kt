package com.joon.ringout.presentation.roomlist

import com.joon.ringout.presentation.roomlist.model.RoomUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoomUiModelTest {
    @Test
    fun `개설일을 시안 형식으로 표시한다`() {
        assertEquals(
            "2026년 9월 15일 개설",
            previewRoom.copy(createdAt = "2026-09-15T09:00:00").createdAtText,
        )
    }

    @Test
    fun `소개 문구가 비어 있어도 서버 응답을 모임 모델로 표현한다`() {
        assertEquals("", previewRoom.copy(description = "").description)
        assertEquals(" \n\t", previewRoom.copy(description = " \n\t").description)
    }

    @Test
    fun `개설일 누락이나 잘못된 날짜는 모임 모델 생성 오류다`() {
        assertFailsWith<IllegalArgumentException> {
            previewRoom.copy(createdAt = " ")
        }
        assertFailsWith<IllegalArgumentException> {
            previewRoom.copy(createdAt = "2026-02-30")
        }
        assertFailsWith<IllegalArgumentException> {
            previewRoom.copy(createdAt = "2026-09-15T00:00:00Z")
        }
        assertFailsWith<IllegalArgumentException> {
            previewRoom.copy(createdAt = "2026-09-15T25:00:00")
        }
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
