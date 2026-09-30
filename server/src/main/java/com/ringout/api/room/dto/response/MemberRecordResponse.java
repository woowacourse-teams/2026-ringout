package com.ringout.api.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MemberRecordResponse(
    @Schema(description = "회원 식별자", example = "1") Long userId,
    @Schema(description = "회원 닉네임", example = "아이아티스트님") String nickname,
    @Schema(description = "회원 프로필 이미지 URL", nullable = true) String profileImageUrl,
    @Schema(description = "회원 활동 기록") List<ActivityRecordResponse> records
) {
}
