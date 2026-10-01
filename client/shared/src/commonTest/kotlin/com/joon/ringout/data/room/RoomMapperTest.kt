package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomMapperTest {
    @Test
    fun `서버 모임 필드를 Domain 모델에 그대로 옮긴다`() {
        val result = roomEntity().toDomain()

        assertEquals(1L, result.id)
        assertEquals("아침 운동 모임", result.name)
        assertEquals(null, result.description)
        assertEquals(listOf("MONDAY", "WEDNESDAY", "FRIDAY"), result.activityDays)
        assertEquals("08:00", result.activityTime)
        assertEquals(12, result.memberCount)
        assertEquals(true, result.isJoined)
        assertEquals("2026-09-20T10:30:00", result.createdAt)
    }

    @Test
    fun `서버 기본 이미지와 비어 있는 이미지 주소는 이미지 없음으로 변환한다`() {
        listOf(null, "", " \n ", "/images/default-room.png").forEach { imageUrl ->
            assertNull(roomEntity(imageUrl = imageUrl).toDomain().imageUrl)
        }
    }

    @Test
    fun `상대 이미지 경로를 개발 서버 절대 주소로 변환한다`() {
        assertEquals(
            "${ApiConfig.BASE_URL}/images/rooms/room-1.png",
            roomEntity(imageUrl = "/images/rooms/room-1.png").toDomain().imageUrl,
        )
        assertEquals(
            "${ApiConfig.BASE_URL}/images/rooms/room-1.png",
            roomEntity(imageUrl = "images/rooms/room-1.png").toDomain().imageUrl,
        )
    }

    @Test
    fun `절대 이미지 주소는 그대로 유지한다`() {
        val imageUrl = "https://cdn.example.com/room.png"

        assertEquals(imageUrl, roomEntity(imageUrl = imageUrl).toDomain().imageUrl)
    }
}

private fun roomEntity(imageUrl: String? = "/images/default-room.png") = RoomEntity(
    roomId = 1,
    name = "아침 운동 모임",
    description = null,
    imageUrl = imageUrl,
    activityDays = listOf("MONDAY", "WEDNESDAY", "FRIDAY"),
    activityTime = "08:00",
    memberCount = 12,
    isJoined = true,
    createdAt = "2026-09-20T10:30:00",
)
