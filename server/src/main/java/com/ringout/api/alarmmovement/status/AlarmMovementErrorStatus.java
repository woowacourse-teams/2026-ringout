package com.ringout.api.alarmmovement.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AlarmMovementErrorStatus implements BaseErrorCode {

    MOVEMENT_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "MOVEMENT401", "인증되지 않은 사용자입니다."),
    MOVEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "MOVEMENT403", "해당 모임의 회원이 아닙니다."),
    MOVEMENT_ACTION_FORBIDDEN(HttpStatus.FORBIDDEN, "MOVEMENT403", "해당 알람에 대한 이동 상태를 변경할 권한이 없습니다."),
    ALARM_NOT_FOUND(HttpStatus.NOT_FOUND, "ALARM404", "존재하지 않는 알람 입니다."),
    MOVEMENT_REQUEST_EMPTY(HttpStatus.BAD_REQUEST, "MOVEMENT400", "이동 요청이 없습니다."),
    MOVEMENT_ACTIVE_ALARM_ID_REQUIRED(HttpStatus.BAD_REQUEST, "MOVEMENT400", "활성 알람 ID가 필요합니다."),
    MOVEMENT_ACTIVE_ALARM_ID_INVALID(HttpStatus.BAD_REQUEST, "MOVEMENT400", "활성 알람 ID가 올바르지 않습니다."),
    MOVEMENT_ACTION_REQUIRED(HttpStatus.BAD_REQUEST, "MOVEMENT400", "이동 행동이 필요합니다."),
    MOVEMENT_NOT_TRIGGERED(HttpStatus.CONFLICT, "MOVEMENT409", "알람이 시작되지 않은 상태에서는 이동 상태를 변경할 수 없습니다."),
    MOVEMENT_ALREADY_STARTED(HttpStatus.CONFLICT, "MOVEMENT409", "이미 이동을 시작한 상태입니다."),
    MOVEMENT_ALREADY_GAVE_UP(HttpStatus.CONFLICT, "MOVEMENT409", "이미 이동을 포기한 알람입니다."),
    MOVEMENT_ALREADY_ARRIVED(HttpStatus.CONFLICT, "MOVEMENT409", "이미 목적지에 도착한 알람입니다."),
    MOVEMENT_TERMINAL_STATE_CONFLICT(HttpStatus.CONFLICT, "MOVEMENT409", "이동 포기 시각과 도착 시각은 동시에 존재할 수 없습니다."),
    MOVEMENT_RECORD_MISSING(HttpStatus.INTERNAL_SERVER_ERROR, "MOVEMENT500", "이동 상태를 처리할 수 없습니다."),
    MOVEMENT_TERMINAL_STATE_INCONSISTENT(HttpStatus.INTERNAL_SERVER_ERROR, "MOVEMENT500", "이동 상태를 조회할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ErrorReasonResponse getReason() {
        return new ErrorReasonResponse(null, false, code, message);
    }

    @Override
    public ErrorReasonResponse getReasonHttpStatus() {
        return new ErrorReasonResponse(httpStatus, false, code, message);
    }
}
