package com.ringout.api.common.response.code.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SuccessStatus implements BaseCode {

    OK(HttpStatus.OK, "COMMON200", "성공입니다."),
    CREATED(HttpStatus.CREATED, "COMMON201", "생성에 성공했습니다."),

    TERMS_AGREED(HttpStatus.CREATED, "TERMS201", "약관 동의가 저장되었습니다."),
    STAMP_CREATED(HttpStatus.CREATED, "STAMP201", "목적지 도착 성공 결과가 저장되었습니다."),
    STAMP_GIVE_UP(HttpStatus.CREATED, "STAMP201", "목표 실패가 기록되었습니다.");

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
