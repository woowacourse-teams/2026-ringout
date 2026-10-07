package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.domain.room.RoomMembershipRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomMapperTest {
    @Test
    fun `일반 회원이 조회해도 상세 응답의 회원별 권한을 구분한다`() {
        val response = com.joon.ringout.data.network.ApiJson.decodeFromString<RoomMembershipResponseEntity>(
            """{
                "roomId":7,"name":"모임","description":null,"imageUrl":null,
                "activityDays":["MONDAY"],"activityTime":"08:00","memberCount":2,
                "membershipRole":"MEMBER","createdAt":"2026-10-01T08:30:00",
                "members":[
                    {"userId":11,"nickname":"방장","membershipRole":"OWNER"},
                    {"userId":12,"nickname":"회원","membershipRole":"MEMBER"}
                ]
            }""",
        )
        val details = response.toDomain(setOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER))

        assertEquals(RoomMembershipRole.MEMBER, details.membershipRole)
        assertEquals(
            listOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER),
            details.members.map { it.membershipRole },
        )
    }

    @Test
    fun `기존 서버가 회원 역할을 생략하면 알 수 없는 역할로 읽는다`() {
        val member = com.joon.ringout.data.network.ApiJson.decodeFromString<RoomMemberEntity>(
            """{"userId":1,"nickname":"기존 회원"}""",
        )
        assertNull(member.membershipRole)
    }

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

    @Test
    fun `수정 응답은 네 필드만 Domain 결과로 변환하고 이미지 주소를 정규화한다`() {
        val result = RoomUpdateResponseEntity(
            roomId = 7,
            name = "수정된 모임",
            description = "",
            imageUrl = "/images/rooms/updated.png",
        ).toDomain()

        assertEquals(7L, result.roomId)
        assertEquals("수정된 모임", result.name)
        assertEquals("", result.description)
        assertEquals("${ApiConfig.BASE_URL}/images/rooms/updated.png", result.imageUrl)
    }

    @Test
    fun `수정 응답의 null과 기본 이미지는 이미지 없음으로 변환한다`() {
        assertNull(RoomUpdateResponseEntity(7, "수정된 모임", null, null).toDomain().imageUrl)
        assertNull(RoomUpdateResponseEntity(7, "수정된 모임", null, "/images/default-room.png").toDomain().imageUrl)
    }

    @Test
    fun `상세 회원의 순서와 프로필 이미지 주소를 Domain 모델에 옮긴다`() {
        val details = RoomMembershipResponseEntity(
            roomId = 7,
            name = "상세 응답",
            description = null,
            imageUrl = "/images/default-room.png",
            activityDays = listOf("MONDAY"),
            activityTime = "08:00",
            memberCount = 2,
            membershipRole = "OWNER",
            createdAt = "2026-10-01T08:30:00",
            members = listOf(
                RoomMemberEntity(11, "두 번째", "/images/profile/member-11.png", "OWNER"),
                RoomMemberEntity(10, "첫 번째", null),
            ),
        ).toDomain(setOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER))

        assertEquals(listOf(11L, 10L), details.members.map { it.userId })
        assertEquals(listOf(RoomMembershipRole.OWNER, null), details.members.map { it.membershipRole })
        assertEquals(listOf("두 번째", "첫 번째"), details.members.map { it.nickname })
        assertEquals(
            listOf("${ApiConfig.BASE_URL}/images/profile/member-11.png", null),
            details.members.map { it.profileImageUrl },
        )
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
