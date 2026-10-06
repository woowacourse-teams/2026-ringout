package com.joon.ringout.presentation.roomlist

import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.presentation.roomlist.model.toRoomUiModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RoomUiModelMapperTest {
    @Test
    fun `서버 예시 모임 정보를 화면 표시 형식으로 변환한다`() {
        val room = roomSummary().toRoomUiModel()

        assertEquals("1", room.id)
        assertEquals("아침 운동 모임", room.name)
        assertEquals("매주 함께 운동해요.", room.description)
        assertEquals(null, room.representativeImage)
        assertEquals("월 수 금", room.activityDaysText)
        assertEquals("오전 8:00", room.activityTimeText)
        assertEquals(12, room.participantCount)
        assertEquals(true, room.isJoined)
        assertEquals("2026년 9월 20일 개설", room.createdAtText)
    }

    @Test
    fun `요일을 월요일부터 일요일 순서로 요약한다`() {
        assertEquals("매일", roomSummary(activityDays = listOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")).toRoomUiModel().activityDaysText)
        assertEquals("평일", roomSummary(activityDays = listOf("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY")).toRoomUiModel().activityDaysText)
        assertEquals("주말", roomSummary(activityDays = listOf("SATURDAY", "SUNDAY")).toRoomUiModel().activityDaysText)
        assertEquals(
            listOf("월", "토", "일"),
            roomSummary(activityDays = listOf("SUNDAY", "MONDAY", "SATURDAY")).toRoomUiModel().activityDays,
        )
    }

    @Test
    fun `자정 정오와 밤 시간의 오전 오후 표시를 맞춘다`() {
        assertEquals("오전 12:00", roomSummary(activityTime = "00:00").toRoomUiModel().activityTimeText)
        assertEquals("오후 12:00", roomSummary(activityTime = "12:00").toRoomUiModel().activityTimeText)
        assertEquals("오후 11:59", roomSummary(activityTime = "23:59").toRoomUiModel().activityTimeText)
    }

    @Test
    fun `nullable 소개는 빈 문자열로 유지하고 소수초 개설일은 날짜를 표시한다`() {
        val room = roomSummary(description = null, createdAt = "2026-09-20T10:30:00.123456789").toRoomUiModel()

        assertEquals("", room.description)
        assertEquals("2026년 9월 20일 개설", room.createdAtText)
    }

    @Test
    fun `알 수 없는 요일과 잘못된 시간은 매핑 오류로 처리한다`() {
        assertFailsWith<IllegalStateException> {
            roomSummary(activityDays = listOf("FUNDAY")).toRoomUiModel()
        }
        assertFailsWith<IllegalArgumentException> {
            roomSummary(activityTime = "24:00").toRoomUiModel()
        }
        assertFailsWith<IllegalArgumentException> {
            roomSummary(activityTime = "8:00").toRoomUiModel()
        }
    }
}

private fun roomSummary(
    description: String? = "매주 함께 운동해요.",
    activityDays: List<String> = listOf("MONDAY", "WEDNESDAY", "FRIDAY"),
    activityTime: String = "08:00",
    createdAt: String = "2026-09-20T10:30:00",
) = RoomSummary(
    id = 1,
    name = "아침 운동 모임",
    description = description,
    imageUrl = null,
    activityDays = activityDays,
    activityTime = activityTime,
    memberCount = 12,
    isJoined = true,
    createdAt = createdAt,
)
