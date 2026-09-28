package com.ringout.api.room.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RoomErrorStatus implements BaseErrorCode {

    ROOM_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ROOM401", "인증되지 않은 사용자입니다."),
    ROOM_NAME_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 방 이름의 형식이 올바르지 않습니다."),
    ROOM_DESCRIPTION_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 소개의 형식이 올바르지 않습니다."),
    ROOM_ACTIVITY_DAYS_REQUIRED(HttpStatus.BAD_REQUEST, "ROOM400", "활동 요일을 1개 이상 선택해야 합니다."),
    ROOM_ACTIVITY_DAY_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "올바르지 않은 활동 요일입니다."),
    ROOM_ACTIVITY_TIME_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "활동 시간의 형식이 올바르지 않습니다."),
    ROOM_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 방 요청 형식이 올바르지 않습니다.");

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
