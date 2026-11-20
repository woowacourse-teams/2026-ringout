package com.ringout.api.forest.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record ForestResponse(
    @Schema(description = "씨앗 성장 상태. 서버의 도착 성공 기록으로 계산")
    ForestGrowthResponse growth,
    @Schema(description = "숲 온보딩 완료 시각. 미완료면 null", example = "2026-11-10T08:00:00+09:00", nullable = true)
    OffsetDateTime onboardingCompletedAt,
    @Schema(description = "심어진 나무 목록. growthCycle 오름차순. 없으면 빈 배열")
    List<ForestTreeResponse> trees
) {
}
