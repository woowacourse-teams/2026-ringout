package com.ringout.api.room.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RoomSuccessStatus implements BaseCode {

    ROOM_CREATED(HttpStatus.CREATED, "ROOM201", "모임 방이 생성되었습니다."),
    ROOM_UPDATED(HttpStatus.OK, "ROOM200", "방 정보 수정에 성공했습니다."),
    ROOM_DELETED(HttpStatus.OK, "ROOM200", "방 삭제에 성공했습니다."),
    ROOM_MEMBER_KICKED(HttpStatus.OK, "ROOM200", "회원 추방에 성공했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    @Override
    public ReasonResponse getReason() {
        return new ReasonResponse(null, true, code, message);
    }

    @Override
    public ReasonResponse getReasonHttpStatus() {
        return new ReasonResponse(httpStatus, true, code, message);
    }
}
