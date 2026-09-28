package com.ringout.api.room.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import com.ringout.api.room.repository.RoomBlackListRepository;
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
    private final RoomBlackListRepository roomBlackListRepository;
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

        // TODO: imageURL 어떻게 관리해야하는지 알아야함. 우선 임시 링크 반환
        return RoomCreateResponse.from(savedRoom, roomUser, DEFAULT_ROOM_IMAGE_URL);
    }

    @Transactional
    public RoomUpdateResponse updateRoom(Long userId, Long roomId, RoomUpdateRequest request) {
        log.atInfo()
            .addKeyValue("event", "room_update_requested")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", roomId)
            .addKeyValue("hasName", request != null && request.name() != null)
            .addKeyValue("descriptionLength", request == null || request.description() == null
                ? null : request.description().length())
            .addKeyValue("hasImage", request != null && request.image() != null)
            .log("모임방 수정 요청 시작");

        User user = findAuthenticatedUser(userId);
        validateUpdateRequest(request);

        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));
        if (!room.isHostedBy(user.getId())) {
            throw new GeneralException(RoomErrorStatus.ROOM_FORBIDDEN);
        }

        if (request.name() != null || request.description() != null) {
            room.update(request.name(), request.description());
        }

        log.atInfo()
            .addKeyValue("event", "room_update_succeeded")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", room.getId())
            .addKeyValue("updatedName", request.name() != null)
            .addKeyValue("updatedDescription", request.description() != null)
            .addKeyValue("updatedImage", request.image() != null)
            .log("모임방 수정 성공");

        // TODO: imageURL 어떻게 관리해야하는지 알아야함. 우선 임시 링크 반환
        return RoomUpdateResponse.from(room, DEFAULT_ROOM_IMAGE_URL);
    }

    @Transactional
    public void deleteRoom(Long userId, Long roomId) {
        log.atInfo()
            .addKeyValue("event", "room_deletion_requested")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", roomId)
            .log("모임방 삭제 요청 시작");

        User user = findAuthenticatedUser(userId);
        Room room = findActiveRoom(roomId);
        if (!room.isHostedBy(user.getId())) {
            throw new GeneralException(RoomErrorStatus.ROOM_DELETE_FORBIDDEN);
        }

        room.softDelete();
        
        // TODO: 아직 추방 기능이 구현되지 않았기 때문에 hard deleted로 구현했습니다.
        roomBlackListRepository.deleteAllByRoom(room);

        log.atInfo()
            .addKeyValue("event", "room_deletion_succeeded")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", room.getId())
            .log("모임방 삭제 성공");
    }

    private User findAuthenticatedUser(Long userId) {
        if (userId == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_UNAUTHORIZED);
        }

        return userRepository.findById(userId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_UNAUTHORIZED));
    }

    private Room findActiveRoom(Long roomId) {
        return roomRepository.findActiveById(roomId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));
    }

    private void validateRequestExists(RoomCreateRequest request) {
        if (request == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_REQUEST_INVALID);
        }
    }

    private void validateUpdateRequest(RoomUpdateRequest request) {
        if (request == null || request.hasNoUpdateField()) {
            throw new GeneralException(RoomErrorStatus.ROOM_UPDATE_REQUIRED);
        }

        if (request.hasInvalidImage()) {
            throw new GeneralException(RoomErrorStatus.ROOM_IMAGE_INVALID);
        }
    }

}
