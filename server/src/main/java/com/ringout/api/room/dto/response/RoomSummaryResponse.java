package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

public record RoomSummaryResponse(
    Long roomId,
    String name,
    String description,
    String imageUrl,
    List<String> activityDays,
    String activityTime,
    Integer memberCount,
    Boolean isJoined,
    LocalDateTime createdAt
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
