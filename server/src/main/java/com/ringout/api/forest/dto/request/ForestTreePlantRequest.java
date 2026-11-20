package com.ringout.api.forest.dto.request;

import com.ringout.api.forest.domain.TreeKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record ForestTreePlantRequest(
    @Schema(description = "나무 종류", example = "pine", type = "string", allowableValues = {"pine", "oak", "big_tree"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    TreeKind kind,
    @Schema(description = "나무를 획득하게 한 도착 시각 (ISO-8601, offset 포함)", example = "2026-11-05T07:48:10+09:00",
        requiredMode = Schema.RequiredMode.REQUIRED)
    OffsetDateTime plantedAt
) {
}
