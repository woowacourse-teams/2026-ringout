package com.ringout.api.room.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private static final String DEFAULT_ROOM_IMAGE_URL = "/images/default-room.png";
    private final RoomRepository roomRepository;
    private final RoomUserRepository roomUserRepository;
    private final UserRepository userRepository;

    @Transactional
    public RoomCreateResponse createRoom(Long userId, RoomCreateRequest request) {
        log.atInfo()
            .addKeyValue("event", "room_creation_requested")
            .addKeyValue("userId", userId)
            .log("모임방 생성 요청 시작");

        User user = findAuthenticatedUser(userId);
        validateRequestExists(request);
        Room room = Room.of(user, null, request.name(), request.description(), request.activityDays(),
            request.activityTime());

        Room savedRoom = roomRepository.save(room);
        RoomUser roomUser = roomUserRepository.save(RoomUser.of(user, savedRoom));

        log.atInfo()
            .addKeyValue("event", "room_creation_succeeded")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", savedRoom.getId())
            .addKeyValue("activityDayCount", savedRoom.getActivityDays().size())
            .log("모임방 생성 성공");

        // TODO: imageURL 어떻게 관리해야하는지 알아야함.
        return RoomCreateResponse.from(savedRoom, roomUser, DEFAULT_ROOM_IMAGE_URL);
    }

    private User findAuthenticatedUser(Long userId) {
        if (userId == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_UNAUTHORIZED);
        }

        return userRepository.findById(userId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_UNAUTHORIZED));
    }

    private void validateRequestExists(RoomCreateRequest request) {
        if (request == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_REQUEST_INVALID);
        }
    }

}
