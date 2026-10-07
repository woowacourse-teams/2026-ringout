package com.ringout.api.alarmmovement.dto.response;

import com.ringout.api.alarmmovement.domain.MovementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberMovementResponse(
    @Schema(description = "사용자 식별자", example = "1") Long userId,
    @Schema(description = "사용자 닉네임", example = "성열") String nickname,
    @Schema(
        description = "프로필 이미지 조회용 S3 presigned URL. 설정하지 않은 경우 null",
        format = "uri",
        nullable = true
    ) String profileImageUrl,
    @Schema(description = "조회 시점의 이동 상태", example = "MOVEMENT_STARTED") MovementStatus status
) {
}
