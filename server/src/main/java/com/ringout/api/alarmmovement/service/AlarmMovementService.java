package com.ringout.api.alarmmovement.service;

import com.ringout.api.alarm.domain.ActiveAlarm;
import com.ringout.api.alarm.repository.ActiveAlarmRepository;
import com.ringout.api.alarmmovement.domain.AlarmMovement;
import com.ringout.api.alarmmovement.domain.MovementAction;
import com.ringout.api.alarmmovement.domain.MovementStatus;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementsResponse;
import com.ringout.api.alarmmovement.repository.AlarmMovementRepository;
import com.ringout.api.alarmmovement.status.AlarmMovementErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import com.ringout.api.room.service.RoomActivityService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final ActiveAlarmRepository activeAlarmRepository;
    private final AlarmMovementRepository alarmMovementRepository;
    private final RoomActivityService roomActivityService;

    private final Clock clock;

    @Transactional
    public AlarmMovementResponse changeMovement(Long userId, Long roomId, AlarmMovementRequest request) {
        validateRequest(request);
        Room room = findActiveRoomForCurrentMember(userId, roomId);

        ActiveAlarm activeAlarm = activeAlarmRepository.findActiveById(request.alarmId())
            .orElseThrow(() -> new GeneralException(AlarmMovementErrorStatus.ALARM_NOT_FOUND));
        AlarmMovement alarmMovement = findAlarmMovement(activeAlarm, request.alarmId());

        MovementStatus movementStatus = alarmMovement.change(request.action(), LocalDateTime.now(clock));
        roomActivityService.recordMovementActivity(room, LocalDateTime.now(clock));
        logMovementStatusChanged(userId, roomId, request.alarmId(), request.action(), movementStatus);

        return new AlarmMovementResponse(movementStatus);
    }

    @Transactional(readOnly = true)
    public MemberMovementsResponse getMemberMovements(Long userId, Long roomId) {
        validateAuthenticatedUser(userId);
        validateRoomId(roomId);
        validateCurrentRoomMemberForMovementStatus(userId, roomId);

        List<RoomUser> roomUsers = roomUserRepository.findActiveByRoomId(roomId);
        Map<Long, ActiveAlarm> latestActiveAlarmByUserId = findLatestActiveAlarmByUserId(roomId);
        Map<ActiveAlarm, AlarmMovement> movementByActiveAlarm = findMovementByActiveAlarm(
            latestActiveAlarmByUserId.values());

        List<MemberMovementResponse> members = roomUsers.stream()
            .map(roomUser -> toMemberMovementResponse(roomUser,
                latestActiveAlarmByUserId.get(roomUser.getUser().getId()), movementByActiveAlarm))
            .sorted(memberNicknameComparator())
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

    private Map<Long, ActiveAlarm> findLatestActiveAlarmByUserId(Long roomId) {
        Map<Long, ActiveAlarm> latestActiveAlarmByUserId = new LinkedHashMap<>();
        activeAlarmRepository.findActiveByRoomIdOrderByIdDesc(roomId)
            .forEach(activeAlarm -> latestActiveAlarmByUserId.putIfAbsent(activeAlarm.getAlarm().getUser().getId(),
                activeAlarm));
        return latestActiveAlarmByUserId;
    }

    private Map<ActiveAlarm, AlarmMovement> findMovementByActiveAlarm(Iterable<ActiveAlarm> activeAlarms) {
        List<ActiveAlarm> activeAlarmList = java.util.stream.StreamSupport.stream(activeAlarms.spliterator(), false)
            .toList();
        if (activeAlarmList.isEmpty()) {
            return Map.of();
        }
        return alarmMovementRepository.findActiveByActiveAlarmIn(activeAlarmList).stream()
            .collect(java.util.stream.Collectors.toMap(AlarmMovement::getActiveAlarm, alarmMovement -> alarmMovement));
    }

    private MemberMovementResponse toMemberMovementResponse(RoomUser roomUser, ActiveAlarm activeAlarm,
        Map<ActiveAlarm, AlarmMovement> movementByActiveAlarm) {
        if (activeAlarm == null) {
            return new MemberMovementResponse(roomUser.getUser().getId(), roomUser.getUser().getNickname().getValue(),
                MovementStatus.IDLE);
        }

        AlarmMovement alarmMovement = movementByActiveAlarm.get(activeAlarm);
        if (alarmMovement == null) {
            log.error("AlarmMovement record is missing. activeAlarmId={}", activeAlarm.getId());
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_RECORD_MISSING);
        }
        if (alarmMovement.getGaveUpAt() != null && alarmMovement.getArrivedAt() != null) {
            log.error("AlarmMovement terminal states conflict. activeAlarmId={}", activeAlarm.getId());
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_TERMINAL_STATE_INCONSISTENT);
        }

        return new MemberMovementResponse(roomUser.getUser().getId(), roomUser.getUser().getNickname().getValue(),
            alarmMovement.getMovementStatus(LocalDateTime.now(clock)));
    }

    private Comparator<MemberMovementResponse> memberNicknameComparator() {
        return Comparator.comparingInt((MemberMovementResponse member) -> nicknameGroup(member.nickname()))
            .thenComparing(MemberMovementResponse::nickname);
    }

    private int nicknameGroup(String nickname) {
        char firstCharacter = nickname.charAt(0);
        if (firstCharacter >= '가' && firstCharacter <= '힣') {
            return 0;
        }
        if ((firstCharacter >= 'A' && firstCharacter <= 'Z') || (firstCharacter >= 'a' && firstCharacter <= 'z')) {
            return 1;
        }
        return 2;
    }

    private AlarmMovement findAlarmMovement(ActiveAlarm activeAlarm, Long activeAlarmId) {
        return alarmMovementRepository.findByActiveAlarm(activeAlarm)
            .orElseThrow(() -> {
                log.error("AlarmMovement record is missing. activeAlarmId={}", activeAlarmId);
                return new GeneralException(AlarmMovementErrorStatus.MOVEMENT_RECORD_MISSING);
            });
    }

    private void logMovementStatusChanged(Long userId, Long roomId, Long activeAlarmId, MovementAction action,
        MovementStatus movementStatus) {
        log.atInfo()
            .addKeyValue("event", "movement_status_changed")
            .addKeyValue("userId", userId)
            .addKeyValue("roomId", roomId)
            .addKeyValue("activeAlarmId", activeAlarmId)
            .addKeyValue("action", action)
            .addKeyValue("movementStatus", movementStatus)
            .log("모임 회원 이동 상태 변경");
    }

    private void validateRequest(AlarmMovementRequest request) {
        validateRequestExists(request);
        validateActiveAlarmIdExists(request.alarmId());
        validateActiveAlarmIdIsPositive(request.alarmId());
        validateMovementActionExists(request.action());
    }

    private void validateRequestExists(AlarmMovementRequest request) {
        if (request == null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_REQUEST_EMPTY);
        }
    }

    private void validateActiveAlarmIdExists(Long activeAlarmId) {
        if (activeAlarmId == null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ACTIVE_ALARM_ID_REQUIRED);
        }
    }

    private void validateActiveAlarmIdIsPositive(Long activeAlarmId) {
        if (activeAlarmId <= 0) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ACTIVE_ALARM_ID_INVALID);
        }
    }

    private void validateMovementActionExists(MovementAction action) {
        if (action == null) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_ACTION_REQUIRED);
        }
    }
}
