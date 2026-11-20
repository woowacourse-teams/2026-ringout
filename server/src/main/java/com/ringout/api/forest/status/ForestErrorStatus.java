package com.ringout.api.forest.status;

import com.ringout.api.common.response.code.BaseErrorCode;
import com.ringout.api.common.response.code.ErrorReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ForestErrorStatus implements BaseErrorCode {

    FOREST_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "FOREST401", "인증되지 않은 사용자입니다."),
    FOREST_ONBOARDING_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "FOREST400", "온보딩 요청 정보가 올바르지 않습니다."),
    FOREST_TREE_GROWTH_CYCLE_INVALID(HttpStatus.BAD_REQUEST, "FOREST400", "나무 회차가 올바르지 않습니다."),
    FOREST_TREE_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "FOREST400", "나무 요청 정보가 올바르지 않습니다."),
    FOREST_TREE_RECORD_MISMATCH(HttpStatus.CONFLICT, "FOREST409", "나무 획득 기록이 서버 기록과 일치하지 않습니다."),
    FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "FOREST400", "나무 확인 요청 정보가 올바르지 않습니다."),
    FOREST_TREE_NOT_FOUND(HttpStatus.NOT_FOUND, "FOREST404", "나무를 찾을 수 없습니다."),
    FOREST_ONBOARDING_NOT_COMPLETED(HttpStatus.CONFLICT, "FOREST409", "숲 온보딩을 완료하지 않았습니다.");

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
