package com.ringout.api.room.service;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.repository.RoomUserRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoomActivityService {

    private final RoomUserRepository roomUserRepository;

    public void recordMemberAlarmActivity(Long userId, LocalDateTime occurredAt) {
        roomUserRepository.findActiveRoomsByUserId(userId)
            .forEach(room -> room.recordActivityAt(occurredAt));
    }

    public void recordMovementActivity(Room room, LocalDateTime occurredAt) {
        room.recordActivityAt(occurredAt);
    }
}
