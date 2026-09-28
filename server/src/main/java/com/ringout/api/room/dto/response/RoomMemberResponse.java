package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.RoomUser;

public record RoomMemberResponse(
    Long userId,
    String nickname,
    String profileImageUrl
) {

    public static RoomMemberResponse from(RoomUser roomUser) {
        return new RoomMemberResponse(
            roomUser.getUser().getId(),
            roomUser.getUser().getNickname().getValue(),
            null
        );
    }
}
