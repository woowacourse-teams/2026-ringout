package com.ringout.api.alarmmovement.dto.request;

import com.ringout.api.alarmmovement.domain.MovementAction;
import io.swagger.v3.oas.annotations.media.Schema;

public record AlarmMovementRequest(
    @Schema(
        description = "이동 상태 변경 대상 활성 알람의 식별자. 양수여야 합니다.",
        example = "135",
        requiredMode = Schema.RequiredMode.REQUIRED,
        minimum = "1"
    )
    Long alarmId,
    @Schema(
        description = "수행할 이동 행동",
        example = "START_MOVEMENT",
        allowableValues = {"START_MOVEMENT", "GIVE_UP", "ARRIVE"},
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    MovementAction action
) {
}
