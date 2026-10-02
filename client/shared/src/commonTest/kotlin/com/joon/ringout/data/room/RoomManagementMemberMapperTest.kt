package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.domain.room.RoomMembershipRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RoomManagementMemberMapperTest {
    @Test
    fun `관리 회원 응답의 순서와 역할 가입일 프로필을 Domain 모델에 옮긴다`() {
        val members = response(
            RoomManagementMemberEntity(
                userId = 11,
                nickname = "두 번째",
                profileImageUrl = "/images/profile/member-11.png",
                joinedAt = "2026-09-20T10:30:00",
                membershipRole = "OWNER",
            ),
            RoomManagementMemberEntity(
                userId = 10,
                nickname = "첫 번째",
                profileImageUrl = "https://cdn.example.com/member.png",
                joinedAt = "2026-09-21T14:20:00",
                membershipRole = "MEMBER",
            ),
        ).toDomain()

        assertEquals(listOf(11L, 10L), members.map { it.userId })
        assertEquals(listOf("두 번째", "첫 번째"), members.map { it.nickname })
        assertEquals(
            listOf(
                "${ApiConfig.BASE_URL}/images/profile/member-11.png",
                "https://cdn.example.com/member.png",
            ),
            members.map { it.profileImageUrl },
        )
        assertEquals(listOf("2026-09-20T10:30:00", "2026-09-21T14:20:00"), members.map { it.joinedAt })
        assertEquals(listOf(RoomMembershipRole.OWNER, RoomMembershipRole.MEMBER), members.map { it.membershipRole })
    }

    @Test
    fun `비어 있는 프로필 주소는 기본 아바타를 사용하도록 null로 변환한다`() {
        val members = response(
            RoomManagementMemberEntity(1, "회원", "  ", "2026-09-20T10:30:00", "MEMBER"),
        ).toDomain()

        assertNull(members.single().profileImageUrl)
    }

    @Test
    fun `중복되거나 양수가 아닌 회원 식별자를 거부한다`() {
        assertFailsWith<IllegalStateException> {
            response(
                member(userId = 1),
                member(userId = 1),
            ).toDomain()
        }
        assertFailsWith<IllegalStateException> {
            response(member(userId = 0)).toDomain()
        }
    }

    @Test
    fun `알 수 없는 회원 역할과 잘못된 가입일을 거부한다`() {
        assertFailsWith<IllegalStateException> {
            response(member(membershipRole = "HOST")).toDomain()
        }
        assertFailsWith<IllegalArgumentException> {
            response(member(joinedAt = "2026-02-30T10:30:00")).toDomain()
        }
    }

    private fun member(
        userId: Long = 1,
        membershipRole: String = "MEMBER",
        joinedAt: String = "2026-09-20T10:30:00",
    ) = RoomManagementMemberEntity(
        userId = userId,
        nickname = "회원 $userId",
        profileImageUrl = null,
        joinedAt = joinedAt,
        membershipRole = membershipRole,
    )

    private fun response(vararg members: RoomManagementMemberEntity) =
        RoomManagementMembersResponseEntity(members.toList())
}
