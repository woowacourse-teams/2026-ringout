package com.ringout.api.user.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserSuccessStatus implements BaseCode {
    USER_FOUND(HttpStatus.OK, "USER200", "회원 정보 조회에 성공했습니다."),
    USER_UPDATED(HttpStatus.OK, "USER200", "회원 정보 수정에 성공했습니다."),
    USER_WITHDRAWN(HttpStatus.OK, "USER200", "회원 탈퇴에 성공했습니다."),
    USER_JOIN_SUCCESS(HttpStatus.CREATED, "USER201", "회원 가입에 성공했습니다.");

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
