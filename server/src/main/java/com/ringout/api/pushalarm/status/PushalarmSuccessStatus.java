package com.ringout.api.pushalarm.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PushalarmSuccessStatus implements BaseCode {

    DEVICE_TOKEN_REGISTERED(HttpStatus.OK, "PUSHALARM200", "디바이스 토큰이 등록되었습니다."),
    DEVICE_TOKEN_DELETED(HttpStatus.OK, "PUSHALARM200", "디바이스 토큰이 삭제되었습니다.");

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
