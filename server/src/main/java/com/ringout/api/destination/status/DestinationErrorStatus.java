package com.ringout.api.destination.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DestinationErrorStatus implements BaseErrorCode {

    DESTINATION_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "DESTINATION401", "인증되지 않은 사용자입니다."),
    DESTINATION_INVALID(HttpStatus.BAD_REQUEST, "DESTINATION400", "목적지 요청 형식이 올바르지 않습니다."),
    DESTINATION_ALIAS_INVALID(HttpStatus.BAD_REQUEST, "DESTINATION400", "위치 별명은 1자 이상 12자 이하로 입력해야 합니다."),
    DESTINATION_COORDINATE_INVALID(HttpStatus.BAD_REQUEST, "DESTINATION400",
        "좌표는 위도 -90~90, 경도 -180~180 범위의 숫자여야 합니다."),
    DESTINATION_NOT_FOUND(HttpStatus.NOT_FOUND, "DESTINATION404", "존재하지 않는 목적지입니다.");

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
