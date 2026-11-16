package com.ringout.api.forest.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ForestErrorStatus implements BaseErrorCode {

    FOREST_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "FOREST401", "인증되지 않은 사용자입니다."),
    FOREST_ONBOARDING_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "FOREST400", "온보딩 요청 정보가 올바르지 않습니다.");

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
