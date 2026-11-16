package com.ringout.api.forest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record ForestOnboardingCompleteRequest(
    @Schema(description = "숲 온보딩을 완료한 시각 (ISO-8601, offset 포함)", example = "2026-11-10T08:00:00+09:00",
        requiredMode = Schema.RequiredMode.REQUIRED)
    OffsetDateTime completedAt
) {
}
