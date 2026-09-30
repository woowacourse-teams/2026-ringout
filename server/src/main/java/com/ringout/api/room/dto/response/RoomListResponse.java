package com.ringout.api.room.dto.response;

import java.util.List;

public record RoomListResponse(
    List<RoomSummaryResponse> rooms
) {
}
