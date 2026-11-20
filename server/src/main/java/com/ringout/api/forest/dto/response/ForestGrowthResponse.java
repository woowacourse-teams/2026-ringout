package com.ringout.api.forest.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ringout.api.forest.domain.ForestGrowth;
import io.swagger.v3.oas.annotations.media.Schema;

public record ForestGrowthResponse(
    @Schema(description = "도착에 성공한 서로 다른 날짜 수. 80일 이후에도 계속 증가", example = "23")
    int successfulDayCount,
    @Schema(description = "현재 씨앗의 진행 칸 수 (0~4). 숲 완성 시 0", example = "3")
    int filledSlots,
    @Schema(description = "다음 나무까지 남은 성공일 수 (1~5). 숲 완성 시 0", example = "2")
    int remainingDays,
    @JsonProperty("isComplete")
    @Schema(description = "숲 완성 여부 (성공일 80일 이상)", example = "false")
    boolean isComplete
) {

    public static ForestGrowthResponse from(ForestGrowth growth) {
        return new ForestGrowthResponse(
            growth.getSuccessfulDayCount(),
            growth.getFilledSlots(),
            growth.getRemainingDays(),
            growth.isComplete()
        );
    }
}
