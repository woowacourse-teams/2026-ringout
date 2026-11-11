package com.ringout.api.alarm.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AlarmSuccessStatus implements BaseCode {

    ALARM_UPSERTED(HttpStatus.OK, "ALARM200", "알람 동기화에 성공했습니다."),
    ALARMS_FOUND(HttpStatus.OK, "ALARM200", "전체 알람 조회에 성공했습니다.");

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
