package com.ringout.api.alarmoccurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record AlarmOccurrenceStartRequest(
    @Schema(description = "클라이언트가 보유한 알람 식별자 (최대 64자)", example = "8f2c1d4e-7a9b-4c3d-9e1f-2a3b4c5d6e7f")
    String alarmId,
    @Schema(description = "이번 울림의 예정 일시. 시각 부분이 알람 설정 시각으로 저장됩니다.",
        example = "2026-09-23T07:00:00+09:00")
    OffsetDateTime scheduledAt,
    @Schema(description = "기기에서 최초 울림이 실제로 발생한 시각", example = "2026-09-23T07:00:02+09:00")
    OffsetDateTime startedAt
) {
}
