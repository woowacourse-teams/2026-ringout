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
    ROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "모임 방을 수정할 권한이 없습니다."),
    ROOM_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "모임 방을 삭제할 권한이 없습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM404", "존재하지 않는 모임 방입니다."),
    ROOM_NAME_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 방 이름의 형식이 올바르지 않습니다."),
    ROOM_DESCRIPTION_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 소개의 형식이 올바르지 않습니다."),
    ROOM_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 대표 이미지의 형식이 올바르지 않습니다."),
    ROOM_UPDATE_REQUIRED(HttpStatus.BAD_REQUEST, "ROOM400", "수정할 정보를 하나 이상 입력해주세요."),
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
