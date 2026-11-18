package com.ringout.api.room.service;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.dto.response.RoomSummaryResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * 캐시에 저장하는 활성 모임방 스냅샷.
 * 로컬 캐시는 참조를 그대로 공유하므로 엔티티 대신 불변 값만 담는다.
 */
public record ActiveRoom(
    Long roomId,
    String name,
    String description,
    String imageUrl,
    List<String> activityDays,
    String activityTime,
    int memberCount,
    LocalDateTime createdAt
) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public ActiveRoom {
        activityDays = List.copyOf(activityDays);
    }

    public static ActiveRoom from(Room room, String imageUrl, int memberCount) {
        return new ActiveRoom(
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
            room.getCreated_at()
        );
    }

    public RoomSummaryResponse toResponse(boolean isJoined) {
        return new RoomSummaryResponse(
            roomId, name, description, imageUrl, activityDays, activityTime, memberCount, isJoined, createdAt
        );
    }
}
