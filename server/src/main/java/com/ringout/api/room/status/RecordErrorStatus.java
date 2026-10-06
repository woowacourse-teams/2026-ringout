package com.ringout.api.room.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RecordErrorStatus implements BaseErrorCode {

    RECORD_DATE_INVALID(HttpStatus.BAD_REQUEST, "RECORD400", "조회 날짜의 형식이 올바르지 않습니다."),
    RECORD_FORBIDDEN(HttpStatus.FORBIDDEN, "RECORD403", "해당 모임의 회원이 아닙니다.");

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
