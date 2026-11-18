package com.ringout.api.room.service;

import com.ringout.api.config.CacheConfig;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.service.ImageFileService;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.repository.RoomMemberCount;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모든 사용자에게 같은 활성 모임방 목록을 읽는다.
 * 캐시 프록시가 적용되도록 RoomService와 다른 빈으로 둔다.
 */
@Component
@RequiredArgsConstructor
public class ActiveRoomsReader {

    private final RoomRepository roomRepository;
    private final RoomUserRepository roomUserRepository;
    private final ImageFileService imageFileService;

    /**
     * sync = true: 캐시가 빈 순간 몰린 요청 중 하나만 DB를 조회하고, 나머지는 그 결과를 함께 받는다.
     */
    @Cacheable(cacheNames = CacheConfig.ACTIVE_ROOMS, key = "'all'", sync = true)
    @Transactional(readOnly = true)
    public List<ActiveRoom> read() {
        List<Room> rooms = roomRepository.findAllActiveOrderByLatestActivityAtDescIdAsc();
        if (rooms.isEmpty()) {
            return List.of();
        }

        Map<Long, Long> memberCountByRoomId = roomUserRepository.countActiveMembersByRoom().stream()
            .collect(Collectors.toMap(RoomMemberCount::roomId, RoomMemberCount::count));

        return rooms.stream()
            .map(room -> ActiveRoom.from(
                room,
                resolveImageUrl(room),
                memberCountByRoomId.getOrDefault(room.getId(), 0L).intValue()
            ))
            .toList();
    }

    private String resolveImageUrl(Room room) {
        ImageFile image = room.getImage();
        if (image == null) {
            return null;
        }
        return imageFileService.createReadUri(image).toString();
    }
}
