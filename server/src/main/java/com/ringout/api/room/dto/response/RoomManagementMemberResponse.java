package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record RoomManagementMemberResponse(
    @Schema(description = "회원 식별자", example = "1") Long userId,
    @Schema(description = "회원 닉네임", example = "가나다") String nickname,
    @Schema(
        description = "프로필 이미지 조회용 S3 presigned URL. 설정하지 않은 경우 null",
        format = "uri",
        nullable = true
    ) String profileImageUrl,
    @Schema(description = "모임 참여 일시", example = "2026-09-20T10:30:00", format = "date-time") LocalDateTime joinedAt,
    @Schema(description = "모임 내 회원 역할", allowableValues = {"OWNER", "MEMBER"}) String membershipRole
) {

    public static RoomManagementMemberResponse from(RoomUser roomUser, Room room, String profileImageUrl) {
        return new RoomManagementMemberResponse(
            roomUser.getUser().getId(),
            roomUser.getUser().getNickname().getValue(),
            profileImageUrl,
            roomUser.getCreated_at(),
            room.isHostedBy(roomUser.getUser().getId()) ? "OWNER" : "MEMBER"
        );
    }
}
