package com.ringout.api.room.dto.response;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

public record RoomListResponse(
    @Schema(description = "활성 모임 방 목록") List<RoomSummaryResponse> rooms
) {
}
