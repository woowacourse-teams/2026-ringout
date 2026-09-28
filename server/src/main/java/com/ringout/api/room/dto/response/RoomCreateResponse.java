package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public record RoomCreateResponse(
    Long roomId,
    String name,
    String description,
    String imageUrl,
    List<String> activityDays,
    String activityTime,
    Integer memberCount,
    String membershipRole,
    LocalDateTime createdAt,
    List<RoomMemberResponse> members
) {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static RoomCreateResponse from(Room room, RoomUser roomUser, String imageUrl) {
        return new RoomCreateResponse(
            room.getId(),
            room.getName(),
            room.getDescription(),
            imageUrl,
            room.getActivityDays().stream().map(Enum::name).toList(),
            room.getActivityTime().format(TIME_FORMATTER),
            1,
            "OWNER",
            room.getCreated_at(),
            List.of(RoomMemberResponse.from(roomUser))
        );
    }
}
