package com.joon.ringout.data.room

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.domain.room.RoomCreateInput
import com.joon.ringout.domain.room.RoomMemberDetails
import com.joon.ringout.domain.room.RoomMembershipDetails
import com.joon.ringout.domain.room.RoomMembershipRole
import com.joon.ringout.domain.room.RoomSummary

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
            )
        },
    )
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
