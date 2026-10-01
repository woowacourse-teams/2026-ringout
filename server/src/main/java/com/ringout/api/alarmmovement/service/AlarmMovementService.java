package com.ringout.api.alarmmovement.service;

import com.ringout.api.alarmmovement.domain.MovementAction;
import com.ringout.api.alarmmovement.domain.MovementStatus;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementsResponse;
import com.ringout.api.alarmmovement.status.AlarmMovementErrorStatus;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.common.util.NicknameComparator;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.room.service.RoomActivityService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlarmMovementService {

    private final RoomRepository roomRepository;
    private final RoomUserRepository roomUserRepository;
    private final AlarmOccurrenceRepository alarmOccurrenceRepository;
    private final RoomActivityService roomActivityService;

    private final Clock clock;

    @Transactional
    public AlarmMovementResponse changeMovement(Long userId, Long roomId, AlarmMovementRequest request) {
        validateRequest(request);
        Room room = findActiveRoomForCurrentMember(userId, roomId);

        AlarmOccurrence alarmOccurrence = alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(
                request.alarmOccurrenceId())
            .orElseThrow(() -> new GeneralException(AlarmMovementErrorStatus.ALARM_NOT_FOUND));
        validateAlarmOccurrenceOwner(alarmOccurrence, userId);
        MovementStatus movementStatus = alarmOccurrence.changeMovement(request.action(), LocalDateTime.now(clock));
        roomActivityService.recordMovementActivity(room, LocalDateTime.now(clock));
        logMovementStatusChanged(userId, roomId, request.alarmOccurrenceId(), request.action(), movementStatus);

        return new AlarmMovementResponse(movementStatus);
    }

    @Transactional(readOnly = true)
    public MemberMovementsResponse getMemberMovements(Long userId, Long roomId) {
        validateAuthenticatedUser(userId);
        validateRoomId(roomId);
        validateCurrentRoomMemberForMovementStatus(userId, roomId);

        List<RoomUser> roomUsers = roomUserRepository.findActiveByRoomId(roomId);
        Map<Long, AlarmOccurrence> latestAlarmOccurrenceByUserId = findLatestAlarmOccurrenceByUserId(roomId);
        List<MemberMovementResponse> members = roomUsers.stream()
            .map(roomUser -> toMemberMovementResponse(roomUser,
                latestAlarmOccurrenceByUserId.get(roomUser.getUser().getId())))
            .sorted(NicknameComparator.comparing(MemberMovementResponse::nickname))
            .toList();

        return new MemberMovementsResponse(members);
    }

    private Room findActiveRoomForCurrentMember(Long userId, Long roomId) {
        Room room = roomRepository.findActiveById(roomId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));
        if (roomUserRepository.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_FORBIDDEN);
        }
        return room;
    }

    private void validateCurrentRoomMemberForMovementStatus(Long userId, Long roomId) {
        roomRepository.findActiveById(roomId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));

        if (roomUserRepository.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new GeneralException(RoomErrorStatus.ROOM_MEMBER_MOVEMENT_STATUS_FORBIDDEN);
        }
    }

    private void validateAuthenticatedUser(Long userId) {
        if (userId == null) {
            throw new GeneralException(RoomErrorStatus.ROOM_UNAUTHORIZED);
        }
    }

    private void validateRoomId(Long roomId) {
        if (roomId == null || roomId <= 0) {
            throw new GeneralException(RoomErrorStatus.ROOM_ID_INVALID);
        }
    }

    private Map<Long, AlarmOccurrence> findLatestAlarmOccurrenceByUserId(Long roomId) {
        Map<Long, AlarmOccurrence> latestAlarmOccurrenceByUserId = new LinkedHashMap<>();
        alarmOccurrenceRepository.findActiveByRoomIdOrderByStartedAtDescIdDesc(roomId)
            .forEach(alarmOccurrence -> latestAlarmOccurrenceByUserId.putIfAbsent(
                alarmOccurrence.getUser().getId(), alarmOccurrence));
        return latestAlarmOccurrenceByUserId;
    }

    private MemberMovementResponse toMemberMovementResponse(RoomUser roomUser, AlarmOccurrence alarmOccurrence) {
        if (alarmOccurrence == null) {
            return new MemberMovementResponse(roomUser.getUser().getId(), roomUser.getUser().getNickname().getValue(),
                MovementStatus.IDLE);
        }

        return new MemberMovementResponse(roomUser.getUser().getId(), roomUser.getUser().getNickname().getValue(),
            alarmOccurrence.getMovementStatus(LocalDateTime.now(clock)));
    }

    private void validateAlarmOccurrenceOwner(AlarmOccurrence alarmOccurrence, Long userId) {
        if (!alarmOccurrence.isOwnedBy(userId)) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ACTION_FORBIDDEN);
        }
    }

    private void logMovementStatusChanged(Long userId, Long roomId, String alarmOccurrenceId, MovementAction action,
        MovementStatus movementStatus) {
        log.atInfo()
            .addKeyValue("event", "movement_status_changed")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", roomId)
            .addKeyValue("alarmOccurrenceId", alarmOccurrenceId)
            .addKeyValue("action", action)
            .addKeyValue("movementStatus", movementStatus)
            .log("모임 회원 이동 상태 변경");
    }

    private void validateRequest(AlarmMovementRequest request) {
        validateRequestExists(request);
        validateAlarmOccurrenceIdExists(request.alarmOccurrenceId());
        validateAlarmOccurrenceIdFormat(request.alarmOccurrenceId());
        validateMovementActionExists(request.action());
    }

    private void validateRequestExists(AlarmMovementRequest request) {
        if (request == null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_REQUEST_EMPTY);
        }
    }

    private void validateAlarmOccurrenceIdExists(String alarmOccurrenceId) {
        if (alarmOccurrenceId == null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ALARM_OCCURRENCE_ID_REQUIRED);
        }
    }

    private void validateAlarmOccurrenceIdFormat(String alarmOccurrenceId) {
        try {
            if (!UUID.fromString(alarmOccurrenceId).toString().equalsIgnoreCase(alarmOccurrenceId)) {
                throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ALARM_OCCURRENCE_ID_INVALID);
            }
        } catch (IllegalArgumentException exception) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ALARM_OCCURRENCE_ID_INVALID);
        }
    }

    private void validateMovementActionExists(MovementAction action) {
        if (action == null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ACTION_REQUIRED);
        }
    }
}
