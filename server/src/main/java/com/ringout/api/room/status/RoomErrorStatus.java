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
    ROOM_LIST_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "ROOM500", "모임 방 목록을 조회하는 중 오류가 발생했습니다."),
    ROOM_ID_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 방 ID는 양수여야 합니다."),
    ROOM_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "모임 방을 수정할 권한이 없습니다."),
    ROOM_DETAIL_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "참여하지 않은 방입니다."),
    ROOM_MEMBER_MANAGEMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "MEMBER403", "회원 관리 권한이 없습니다."),
    ROOM_JOIN_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "해당 모임에 참여할 수 없는 사용자입니다."),
    ROOM_MEMBER_MOVEMENT_STATUS_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "모임 회원 상태를 조회할 권한이 없습니다."),
    ROOM_DELETE_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "모임 방을 삭제할 권한이 없습니다."),
    ROOM_KICK_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "회원을 추방할 권한이 없습니다."),
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "ROOM404", "존재하지 않는 모임 방입니다."),
    ROOM_ALREADY_JOINED(HttpStatus.CONFLICT, "ROOM409", "이미 참여 중인 모임입니다."),
    ROOM_MEMBER_NOT_JOINED(HttpStatus.CONFLICT, "ROOM409", "참여 중인 모임이 아닙니다."),
    ROOM_MEMBER_NOT_FOUND(HttpStatus.BAD_REQUEST, "ROOM400", "해당 사용자는 모임에 참여하고 있지 않습니다."),
    ROOM_HOST_KICK_FORBIDDEN(HttpStatus.BAD_REQUEST, "ROOM400", "방장은 자신을 추방할 수 없습니다."),
    ROOM_HOST_LEAVE_FORBIDDEN(HttpStatus.FORBIDDEN, "ROOM403", "방장은 모임에서 탈퇴할 수 없습니다."),
    ROOM_NAME_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 방 이름의 형식이 올바르지 않습니다."),
    ROOM_DESCRIPTION_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 소개의 형식이 올바르지 않습니다."),
    ROOM_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "ROOM400", "모임 대표 이미지의 형식이 올바르지 않습니다."),
    ROOM_IMAGE_REMOVE_CONFLICT(HttpStatus.BAD_REQUEST, "ROOM400", "대표 이미지 교체와 기본 이미지 전환을 동시에 요청할 수 없습니다."),
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
