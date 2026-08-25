package com.ringout.api.terms.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TermsErrorStatus implements BaseErrorCode {

    TERMS_NOT_FOUND(HttpStatus.NOT_FOUND, "TERMS404", "존재하지 않는 약관입니다."),
    TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS400", "필수 약관에 모두 동의해야 합니다."),
    TERMS_NOT_EFFECTIVE(HttpStatus.BAD_REQUEST, "TERMS400", "현재 시행 중인 약관이 아닙니다."),
    TERMS_AGREED_AT_INVALID(HttpStatus.BAD_REQUEST, "TERMS400", "동의 시각이 올바르지 않습니다."),
    TERMS_ALREADY_AGREED(HttpStatus.CONFLICT, "TERMS409", "이미 동의한 약관입니다.");

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
