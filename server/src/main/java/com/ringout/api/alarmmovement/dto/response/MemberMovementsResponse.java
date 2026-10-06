package com.ringout.api.alarmmovement.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record MemberMovementsResponse(
    @Schema(description = "모임 회원의 현재 이동 상태 목록") List<MemberMovementResponse> members
) {
}
