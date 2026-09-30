package com.ringout.api.room.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomBlackList;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.dto.request.RoomCreateRequest;
import com.ringout.api.room.dto.request.RoomKickRequest;
import com.ringout.api.room.dto.request.RoomUpdateRequest;
import com.ringout.api.room.dto.response.RoomCreateResponse;
import com.ringout.api.room.dto.response.RoomDetailResponse;
import com.ringout.api.room.dto.response.RoomListResponse;
import com.ringout.api.room.dto.response.RoomSummaryResponse;
import com.ringout.api.room.dto.response.RoomUpdateResponse;
import com.ringout.api.room.repository.RoomBlackListRepository;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.user.status.UserErrorStatus;
import java.util.List;
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

    @Transactional(readOnly = true)
    public RoomListResponse getRooms(Long userId) {
        List<RoomSummaryResponse> rooms = roomRepository.findAllActiveOrderByLatestActivityAtDescIdAsc().stream()
            .map(room -> RoomSummaryResponse.from(
                room,
                DEFAULT_ROOM_IMAGE_URL,
                roomUserRepository.countActiveByRoomId(room.getId()),
                userId != null && roomUserRepository.existsActiveByRoomIdAndUserId(room.getId(), userId)
            ))
            .toList();

        return new RoomListResponse(rooms);
    }

    @Transactional(readOnly = true)
    public RoomDetailResponse getRoom(Long userId, Long roomId) {
        User user = findAuthenticatedUser(userId);
        Room room = findActiveRoom(roomId);

        roomUserRepository.findActiveByRoomIdAndUserId(roomId, user.getId())
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_DETAIL_FORBIDDEN));

        List<RoomUser> roomUsers = roomUserRepository.findActiveByRoomId(roomId);

        return RoomDetailResponse.from(room, user.getId(), roomUsers);
    }

    @Transactional
    public RoomDetailResponse joinRoom(Long userId, Long roomId) {
        User user = findAuthenticatedUser(userId);
        Room room = findActiveRoom(roomId);

        validateJoinRoom(roomId, user);

        roomUserRepository.findByRoomIdAndUserId(roomId, user.getId())
            .ifPresentOrElse(RoomUser::restore, () -> roomUserRepository.save(RoomUser.of(user, room)));
        List<RoomUser> roomUsers = roomUserRepository.findActiveByRoomId(roomId);

        log.atInfo()
            .addKeyValue("event", "room_member_join_succeeded")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", roomId)
            .log("모임방 참여 성공");

        return RoomDetailResponse.from(room, user.getId(), roomUsers);
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
            room.recordActivityAt(java.time.LocalDateTime.now());
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
        roomBlackListRepository.findActiveByRoom(room)
            .forEach(RoomBlackList::softDelete);

        log.atInfo()
            .addKeyValue("event", "room_deletion_succeeded")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", room.getId())
            .log("모임방 삭제 성공");
    }

    @Transactional
    public void kickMember(Long hostUserId, Long roomId, RoomKickRequest request) {
        Long targetUserId = request == null ? null : request.userId();
        log.atInfo()
            .addKeyValue("event", "room_member_kick_requested")
            .addKeyValue("hostUserId", hostUserId)
            .addKeyValue("roomId", roomId)
            .addKeyValue("targetUserId", targetUserId)
            .log("모임 회원 추방 요청 시작");

        User host = findAuthenticatedUser(hostUserId);
        Room room = findActiveRoom(roomId);
        if (!room.isHostedBy(host.getId())) {
            throw new GeneralException(RoomErrorStatus.ROOM_KICK_FORBIDDEN);
        }
        if (host.getId().equals(targetUserId)) {
            throw new GeneralException(RoomErrorStatus.ROOM_HOST_KICK_FORBIDDEN);
        }

        User target = userRepository.findById(targetUserId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));
        RoomUser roomUser = roomUserRepository.findActiveByRoomIdAndUserId(roomId, target.getId())
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_MEMBER_NOT_FOUND));

        roomUser.softDelete();
        roomBlackListRepository.save(RoomBlackList.of(room, target));

        log.atInfo()
            .addKeyValue("event", "room_member_kick_succeeded")
            .addKeyValue("hostUserId", hostUserId)
            .addKeyValue("roomId", roomId)
            .addKeyValue("targetUserId", targetUserId)
            .log("모임 회원 추방 성공");
    }

    @Transactional
    public void leaveRoom(Long userId, Long roomId) {
        log.atInfo()
            .addKeyValue("event", "room_member_leave_requested")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", roomId)
            .log("모임방 탈퇴 요청 시작");

        User user = findAuthenticatedUser(userId);
        Room room = findActiveRoom(roomId);
        if (room.isHostedBy(user.getId())) {
            throw new GeneralException(RoomErrorStatus.ROOM_HOST_LEAVE_FORBIDDEN);
        }

        RoomUser roomUser = roomUserRepository.findActiveByRoomIdAndUserId(roomId, user.getId())
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_MEMBER_NOT_JOINED));
        roomUser.softDelete();

        log.atInfo()
            .addKeyValue("event", "room_member_leave_succeeded")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", roomId)
            .log("모임방 탈퇴 성공");
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

    private void validateJoinRoom(Long roomId, User user) {
        if (roomUserRepository.findActiveByRoomIdAndUserId(roomId, user.getId()).isPresent()) {
            throw new GeneralException(RoomErrorStatus.ROOM_ALREADY_JOINED);
        }
        if (roomBlackListRepository.existsActiveByRoomIdAndUserId(roomId, user.getId())) {
            throw new GeneralException(RoomErrorStatus.ROOM_JOIN_FORBIDDEN);
        }

    }

}
