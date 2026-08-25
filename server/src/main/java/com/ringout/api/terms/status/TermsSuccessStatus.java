package com.ringout.api.terms.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum TermsSuccessStatus implements BaseCode {

    TERMS_AGREED(HttpStatus.CREATED, "TERMS201", "약관 동의가 저장되었습니다."),
    REQUIRED_TERMS_AGREEMENT_CHECKED(HttpStatus.OK, "TERMS200", "필수 약관 동의 여부 조회에 성공했습니다.");

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
