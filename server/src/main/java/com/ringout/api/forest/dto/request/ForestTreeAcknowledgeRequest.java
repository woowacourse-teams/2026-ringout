package com.ringout.api.forest.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record ForestTreeAcknowledgeRequest(
    @Schema(description = "나무 획득 연출을 확인한 시각 (ISO-8601, offset 포함)", example = "2026-11-10T08:01:00+09:00",
        requiredMode = Schema.RequiredMode.REQUIRED)
    OffsetDateTime acknowledgedAt
) {
}
