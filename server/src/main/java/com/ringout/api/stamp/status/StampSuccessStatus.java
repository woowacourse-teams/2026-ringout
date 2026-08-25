package com.ringout.api.stamp.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum StampSuccessStatus implements BaseCode {

    STAMP_CREATED(HttpStatus.CREATED, "STAMP201", "목적지 도착 성공 결과가 저장되었습니다."),
    STAMP_GIVE_UP(HttpStatus.CREATED, "STAMP201", "목표 실패가 기록되었습니다."),
    MONTHLY_STAMPS_FOUND(HttpStatus.OK, "STAMP200", "월별 스탬프 조회에 성공했습니다.");

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
