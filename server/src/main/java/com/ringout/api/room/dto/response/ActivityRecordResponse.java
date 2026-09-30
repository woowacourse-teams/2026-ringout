package com.ringout.api.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record ActivityRecordResponse(
    @Schema(description = "기록 이벤트", example = "ALARM_TRIGGERED") RecordEvent event,
    @Schema(description = "실제 이벤트 발생 시각", example = "2026-09-16T07:00:00+09:00") OffsetDateTime occurredAt,
    @Schema(description = "재울림 차수", example = "1", nullable = true) Integer count
) {
}
