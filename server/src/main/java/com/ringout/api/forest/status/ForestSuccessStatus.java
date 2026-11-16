package com.ringout.api.forest.status;

import com.ringout.api.common.response.code.BaseCode;
import com.ringout.api.common.response.code.ReasonResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ForestSuccessStatus implements BaseCode {

    FOREST_ONBOARDING_COMPLETED(HttpStatus.CREATED, "FOREST201", "숲 온보딩 완료에 성공했습니다."),
    FOREST_ONBOARDING_SYNCED(HttpStatus.OK, "FOREST200", "숲 온보딩 동기화에 성공했습니다."),
    FOREST_TREE_PLANTED(HttpStatus.CREATED, "FOREST201", "나무 저장에 성공했습니다."),
    FOREST_TREE_SYNCED(HttpStatus.OK, "FOREST200", "나무 동기화에 성공했습니다.");

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
