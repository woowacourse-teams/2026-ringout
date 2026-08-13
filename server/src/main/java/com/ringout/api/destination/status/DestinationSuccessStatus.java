package com.ringout.api.destination.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DestinationSuccessStatus implements BaseCode {

    DESTINATION_CREATED(HttpStatus.CREATED, "DESTINATION201", "목적지 생성에 성공했습니다."),
    DESTINATIONS_FOUND(HttpStatus.OK, "DESTINATION200", "조회되었습니다."),
    DESTINATION_DELETED(HttpStatus.OK, "DESTINATION200", "삭제되었습니다."),
    DESTINATION_UPDATED(HttpStatus.OK, "DESTINATION200", "목적지 정보가 수정되었습니다.");

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
