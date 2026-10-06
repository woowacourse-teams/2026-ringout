package com.ringout.api.alarmoccurrence.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AlarmOccurrenceSuccessStatus implements BaseCode {

    ALARM_OCCURRENCE_OK(HttpStatus.OK, "COMMON200", "정상적인 요청입니다."),
    ALARM_OCCURRENCE_CREATED(HttpStatus.CREATED, "COMMON201_1", "알람 실행 기록이 저장되었습니다.");

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
