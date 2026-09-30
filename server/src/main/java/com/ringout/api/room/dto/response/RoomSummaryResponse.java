package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public record RoomSummaryResponse(
    @Schema(description = "모임 방 식별자", example = "1") Long roomId,
    @Schema(description = "모임 방 이름", example = "아침 운동 모임") String name,
    @Schema(description = "모임 소개. 값이 없으면 null", nullable = true) String description,
    @Schema(description = "대표 이미지 URL. 이미지가 없으면 기본 이미지 URL", example = "/images/default-room.png") String imageUrl,
    @Schema(description = "활동 요일. 월요일부터 일요일 순", example = "[\"MONDAY\", \"WEDNESDAY\", \"FRIDAY\"]") List<String> activityDays,
    @Schema(description = "모든 활동 요일에 적용되는 시간 (HH:mm)", example = "08:00", pattern = "^\\d{2}:\\d{2}$") String activityTime,
    @Schema(description = "현재 참여 중인 사용자 수", example = "4") Integer memberCount,
    @Schema(description = "현재 인증 사용자의 참여 여부", example = "true") Boolean isJoined,
    @Schema(description = "방 최초 생성 일시 (ISO 8601)", example = "2026-09-20T10:30:00", format = "date-time") LocalDateTime createdAt
) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static RoomSummaryResponse from(
        Room room,
        String defaultImageUrl,
        int memberCount,
        boolean isJoined
    ) {
        String imageUrl = room.getImage() == null ? defaultImageUrl : room.getImage().getUrl();

        return new RoomSummaryResponse(
            room.getId(),
            room.getName(),
            room.getDescription(),
            imageUrl,
            room.getActivityDays().stream()
                .sorted(Comparator.comparingInt(Enum::ordinal))
                .map(Enum::name)
                .toList(),
            room.getActivityTime().format(TIME_FORMATTER),
            memberCount,
            isJoined,
            room.getCreated_at()
        );
    }
}
