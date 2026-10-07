package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record RoomUpdateResponse(
    @Schema(description = "모임 방 식별자", example = "1") Long roomId,
    @Schema(description = "변경된 모임 방 이름", example = "새로운 아침 운동 모임") String name,
    @Schema(description = "변경된 모임 소개. 값이 없으면 null", nullable = true) String description,
    @Schema(description = "대표 이미지 URL. 이미지가 없으면 null", example = "https://example.com/images/room-1.png", nullable = true) String imageUrl,
    @Schema(description = "변경 후 모임 활동 요일. 월요일부터 일요일 순", example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]") List<String> activityDays,
    @Schema(description = "변경 후 모든 활동 요일에 적용되는 시간 (HH:mm)", example = "19:30", pattern = "^\\d{2}:\\d{2}$") String activityTime
) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static RoomUpdateResponse from(Room room, String imageUrl) {
        return new RoomUpdateResponse(
            room.getId(),
            room.getName(),
            room.getDescription(),
            imageUrl,
            room.getActivityDays().stream().map(Enum::name).toList(),
            room.getActivityTime().format(TIME_FORMATTER)
        );
    }
}
