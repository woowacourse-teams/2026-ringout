package com.ringout.api.alarm.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AlarmErrorStatus implements BaseErrorCode {

    ALARM_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "ALARM401", "인증되지 않은 사용자입니다."),
    ALARM_ID_INVALID(HttpStatus.BAD_REQUEST, "ALARM400", "알람 ID가 올바르지 않습니다."),
    ALARM_DELETE_ID_INVALID(HttpStatus.BAD_REQUEST, "ALARM400", "알람 식별자가 올바르지 않습니다."),
    ALARM_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "ALARM400", "알람 요청 정보가 올바르지 않습니다."),
    ALARM_VERSION_INVALID(HttpStatus.BAD_REQUEST, "ALARM400", "알람 버전 값이 올바르지 않습니다.");

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
