package com.ringout.api.alarmoccurrence.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AlarmOccurrenceErrorStatus implements BaseErrorCode {

    ALARM_OCCURRENCE_REQUEST_EMPTY(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "알람 실행 요청이 없습니다."),
    ALARM_ID_REQUIRED(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "알람 ID가 필요합니다."),
    ALARM_ID_TOO_LONG(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "알람 ID는 64자를 넘을 수 없습니다."),
    SCHEDULED_AT_REQUIRED(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "알람 예정 일시가 필요합니다."),
    ALARM_OCCURRENCE_ID_INVALID(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "알람 실행 ID가 올바르지 않습니다."),
    ALARM_OCCURRENCE_EVENT_EMPTY(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "저장할 알람 실행 이벤트가 없습니다."),
    EVENT_ID_BLANK(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "재울림 ID는 공백일 수 없습니다."),
    EVENT_ID_TOO_LONG(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "재울림 ID는 64자를 넘을 수 없습니다."),
    END_TIMES_CONFLICT(HttpStatus.BAD_REQUEST, "ALARM_OCCURRENCE400", "도착 시각과 강제 종료 시각은 동시에 보낼 수 없습니다."),
    ALARM_OCCURRENCE_FORBIDDEN(HttpStatus.FORBIDDEN, "ALARM_OCCURRENCE403", "해당 알람 실행 기록에 대한 권한이 없습니다."),
    ALARM_OCCURRENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "ALARM_OCCURRENCE404", "존재하지 않는 알람 실행 기록입니다."),
    ALARM_OCCURRENCE_ALREADY_ENDED(HttpStatus.CONFLICT, "ALARM_OCCURRENCE409", "이미 종료된 알람 실행입니다.");

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
