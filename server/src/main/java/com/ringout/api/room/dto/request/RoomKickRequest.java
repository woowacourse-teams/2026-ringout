package com.ringout.api.room.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record RoomKickRequest(
    @Schema(description = "추방할 회원의 식별자", example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    Long userId
) {
}
