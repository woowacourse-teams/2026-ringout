package com.ringout.api.alarmmovement.dto.request;

import com.ringout.api.alarmmovement.domain.MovementAction;
import io.swagger.v3.oas.annotations.media.Schema;

public record AlarmMovementRequest(
    @Schema(
        description = "이동 상태 변경 대상 알람 실행의 UUID 식별자입니다.",
        example = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f",
        requiredMode = Schema.RequiredMode.REQUIRED,
        format = "uuid"
    )
    String alarmOccurrenceId,
    @Schema(
        description = "수행할 이동 행동",
        example = "START_MOVEMENT",
        allowableValues = {"START_MOVEMENT", "GIVE_UP", "ARRIVE"},
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    MovementAction action
) {
}
