package com.ringout.api.member.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorStatus implements BaseErrorCode {
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER404", "존재하지 않는 사용자입니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER409", "이미 가입한 사용자입니다."),
    USER_NICKNAME_REQUIRED(HttpStatus.BAD_REQUEST, "USER400", "닉네임은 비어 있을 수 없습니다."),
    USER_NICKNAME_TOO_LONG(HttpStatus.BAD_REQUEST, "USER400", "닉네임은 최대 10자입니다."),
    USER_NICKNAME_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "USER400", "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.");

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
