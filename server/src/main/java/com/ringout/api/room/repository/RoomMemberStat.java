package com.ringout.api.room.repository;

public record RoomMemberStat(
    Long roomId,
    Long memberCount,
    Long joinedCount
) {

    public boolean isJoined() {
        return joinedCount != null && joinedCount > 0;
    }
}
