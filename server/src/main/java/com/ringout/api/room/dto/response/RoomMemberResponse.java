package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.RoomUser;
import io.swagger.v3.oas.annotations.media.Schema;

public record RoomMemberResponse(
    @Schema(description = "참여자 식별자", example = "1") Long userId,
    @Schema(description = "참여자 닉네임", example = "가나다") String nickname,
    @Schema(description = "프로필 이미지 URL. 설정하지 않은 경우 null", nullable = true) String profileImageUrl
) {

    public static RoomMemberResponse from(RoomUser roomUser) {
        return new RoomMemberResponse(
            roomUser.getUser().getId(),
            roomUser.getUser().getNickname().getValue(),
            roomUser.getUser().getImage() == null ? null : roomUser.getUser().getImage().getUrl()
        );
    }
}
