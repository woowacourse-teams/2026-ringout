package com.ringout.api.room.dto.response;

import com.ringout.api.room.domain.Room;

public record RoomUpdateResponse(
    Long roomId,
    String name,
    String description,
    String imageUrl
) {

    public static RoomUpdateResponse from(Room room, String imageUrl) {
        return new RoomUpdateResponse(room.getId(), room.getName(), room.getDescription(), imageUrl);
    }
}
