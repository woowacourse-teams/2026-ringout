package com.ringout.api.member.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorStatus implements BaseErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "존재하지 않는 사용자입니다."),
    MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "MEMBER409", "이미 가입한 회원입니다."),
    MEMBER_NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "MEMBER400", "닉네임 형식이 올바르지 않습니다.");

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
