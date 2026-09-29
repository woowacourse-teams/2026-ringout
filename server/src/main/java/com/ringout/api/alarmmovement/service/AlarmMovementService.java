package com.ringout.api.alarmmovement.service;

import com.ringout.api.alarm.domain.ActiveAlarm;
import com.ringout.api.alarm.repository.ActiveAlarmRepository;
import com.ringout.api.alarmmovement.domain.AlarmMovement;
import com.ringout.api.alarmmovement.domain.MovementAction;
import com.ringout.api.alarmmovement.domain.MovementStatus;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.repository.AlarmMovementRepository;
import com.ringout.api.alarmmovement.status.AlarmMovementErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RoomErrorStatus;
import java.time.Clock;
import java.time.LocalDateTime;
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
    
    private final Clock clock;

    @Transactional
    public AlarmMovementResponse changeMovement(Long userId, Long roomId, AlarmMovementRequest request) {
        validateRequest(request);
        validateCurrentRoomMember(userId, roomId);

        ActiveAlarm activeAlarm = activeAlarmRepository.findActiveById(request.alarmId())
            .orElseThrow(() -> new GeneralException(AlarmMovementErrorStatus.ALARM_NOT_FOUND));
        AlarmMovement alarmMovement = findAlarmMovement(activeAlarm, request.alarmId());

        MovementStatus movementStatus = alarmMovement.change(request.action(), LocalDateTime.now(clock));
        logMovementStatusChanged(userId, roomId, request.alarmId(), request.action(), movementStatus);

        return new AlarmMovementResponse(movementStatus);
    }

    private void validateCurrentRoomMember(Long userId, Long roomId) {
        roomRepository.findActiveById(roomId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));
        if (roomUserRepository.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new GeneralException(AlarmMovementErrorStatus.MOVEMENT_FORBIDDEN);
        }
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
