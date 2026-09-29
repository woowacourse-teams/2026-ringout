package com.ringout.api.alarmmovement.dto.response;

import com.ringout.api.alarmmovement.domain.MovementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AlarmMovementResponse(
    @Schema(description = "요청 처리 후 사용자의 현재 이동 상태", example = "MOVEMENT_STARTED")
    MovementStatus status
) {
}
