package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import io.swagger.v3.oas.annotations.media.Schema;

public record RoomUpdateResponse(
    @Schema(description = "모임 방 식별자", example = "1") Long roomId,
    @Schema(description = "변경된 모임 방 이름", example = "새로운 아침 운동 모임") String name,
    @Schema(description = "변경된 모임 소개. 값이 없으면 null", nullable = true) String description,
    @Schema(description = "현재 구현의 기본 이미지 URL", example = "/images/default-room.png") String imageUrl
) {

    public static RoomUpdateResponse from(Room room, String imageUrl) {
        return new RoomUpdateResponse(room.getId(), room.getName(), room.getDescription(), imageUrl);
    }
}
