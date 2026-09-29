package com.ringout.api.alarmmovement.dto.request;

import com.ringout.api.alarmmovement.domain.MovementAction;
import io.swagger.v3.oas.annotations.media.Schema;

public record AlarmMovementRequest(
    @Schema(description = "이동 행동의 대상이 되는 활성 알람 식별자", example = "135")
    Long alarmId,
    @Schema(description = "사용자의 이동 행동", example = "START_MOVEMENT")
    MovementAction action
) {
}
