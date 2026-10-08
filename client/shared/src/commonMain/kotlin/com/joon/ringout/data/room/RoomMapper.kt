package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.domain.missionhistory.MissionDate
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomManagementMember
import com.joon.ringout.domain.room.RoomMemberDetails
import com.joon.ringout.domain.room.RoomMemberMovement
import com.joon.ringout.domain.room.RoomMemberMovementStatus
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomSummary
import com.joon.ringout.domain.room.RoomUpdateResult

internal fun RoomEntity.toDomain(): RoomSummary = RoomSummary(
    id = roomId,
    name = name,
    description = description,
    imageUrl = imageUrl.toRoomImageUrl(),
    activityDays = activityDays,
    activityTime = activityTime,
    memberCount = memberCount,
    isJoined = isJoined,
    createdAt = createdAt,
)

internal fun RoomCreateInput.toEntity(): RoomCreateRequestEntity = RoomCreateRequestEntity(
    name = name,
    description = description,
    activityDays = activityDays,
    activityTime = activityTime,
)

internal fun RoomMembershipResponseEntity.toDomain(
    allowedRoles: Set<RoomMembershipRole>,
): RoomMembershipDetails {
    val role = checkNotNull(RoomMembershipRole.entries.firstOrNull { it.name == membershipRole }) {
        "모임 참여 역할을 확인할 수 없어요."
    }
    check(role in allowedRoles) { "모임 참여 역할을 확인할 수 없어요." }
    return RoomMembershipDetails(
        room = RoomSummary(
            id = roomId,
            name = name,
            description = description,
            imageUrl = imageUrl.toRoomImageUrl(),
            activityDays = activityDays,
            activityTime = activityTime,
            memberCount = memberCount,
            isJoined = true,
            createdAt = createdAt,
        ),
        membershipRole = role,
        members = members.map { member ->
            RoomMemberDetails(
                userId = member.userId,
                nickname = member.nickname,
                profileImageUrl = member.profileImageUrl.toRoomImageUrl(),
                membershipRole = RoomMembershipRole.entries.firstOrNull { it.name == member.membershipRole },
            )
        },
    )
}

internal fun RoomManagementMembersResponseEntity.toDomain(): List<RoomManagementMember> {
    check(members.all { it.userId > 0L }) { "회원 식별자를 확인할 수 없어요." }
    check(members.map { it.userId }.distinct().size == members.size) { "중복된 회원 식별자가 있어요." }

    return members.map { member ->
        val role = checkNotNull(RoomMembershipRole.entries.firstOrNull { it.name == member.membershipRole }) {
            "모임 회원 역할을 확인할 수 없어요."
        }
        val joinedDate = member.joinedAt.takeIf { it.length > 10 && it[10] == 'T' }
            ?.take(10)
            ?: error("회원 가입일을 확인할 수 없어요.")
        MissionDate.parse(joinedDate)

        RoomManagementMember(
            userId = member.userId,
            nickname = member.nickname,
            profileImageUrl = member.profileImageUrl.toRoomImageUrl(),
            joinedAt = member.joinedAt,
            membershipRole = role,
        )
    }
}

internal fun RoomMemberMovementsResponseEntity.toDomain(): List<RoomMemberMovement> {
    check(members.all { it.userId > 0L }) { "회원 식별자를 확인할 수 없어요." }
    check(members.map { it.userId }.distinct().size == members.size) { "중복된 회원 식별자가 있어요." }
    check(members.all { it.nickname.isNotBlank() }) { "회원 닉네임을 확인할 수 없어요." }

    return members.map { member ->
        RoomMemberMovement(
            userId = member.userId,
            nickname = member.nickname,
            status = member.status.toRoomMemberMovementStatus(),
            profileImageUrl = member.profileImageUrl.toRoomImageUrl(),
        )
    }
}

internal fun RoomUpdateResponseEntity.toDomain(): RoomUpdateResult = RoomUpdateResult(
    activityDays = activityDays,
    activityTime = activityTime,
    roomId = roomId,
    name = name,
    description = description,
    imageUrl = imageUrl.toRoomImageUrl(),
)

private fun String.toRoomMemberMovementStatus(): RoomMemberMovementStatus = when (this) {
    "IDLE" -> RoomMemberMovementStatus.Idle
    "ALARM_TRIGGERED" -> RoomMemberMovementStatus.AlarmTriggered
    "MOVEMENT_STARTED" -> RoomMemberMovementStatus.MovementStarted
    "MOVING" -> RoomMemberMovementStatus.Moving
    "ARRIVED" -> RoomMemberMovementStatus.Arrived
    "GAVE_UP" -> RoomMemberMovementStatus.GaveUp
    else -> RoomMemberMovementStatus.Unknown
}

internal fun String?.toRoomImageUrl(): String? {
    val value = this?.trim()?.takeIf(String::isNotEmpty) ?: return null
    if (value == DefaultRoomImagePath) return null
    if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) {
        return value
    }
    return ApiConfig.url(value)
}

private const val DefaultRoomImagePath = "/images/default-room.png"
