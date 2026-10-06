package com.ringout.api.alarmoccurrence.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record AlarmOccurrenceEventRequest(
    @Schema(description = "재울림 식별자 (최대 64자). 재전송할 때도 같은 값을 사용합니다.",
        example = "b1e2c3d4-0000-4000-8000-000000000002", nullable = true)
    String eventId,
    @Schema(description = "기기에서 재울림이 실제로 발생한 시각. eventId를 보낼 때 함께 보냅니다.",
        example = "2026-09-23T07:10:00+09:00", nullable = true)
    OffsetDateTime ringingAt,
    @Schema(description = "울림을 끈 시각. eventId가 있으면 해당 재울림, 없으면 최초 울림을 끈 것으로 처리합니다.",
        example = "2026-09-23T07:10:40+09:00", nullable = true)
    OffsetDateTime dismissedAt,
    @Schema(description = "목적지 도착 시각 (미션 성공, 실행 종료)", example = "2026-09-23T07:48:10+09:00", nullable = true)
    OffsetDateTime arrivedAt,
    @Schema(description = "강제 종료 시각 (미션 실패, 실행 종료)", nullable = true)
    OffsetDateTime forceEndedAt
) {

    public boolean isEmpty() {
        return eventId == null && ringingAt == null && dismissedAt == null && arrivedAt == null
            && forceEndedAt == null;
    }
}
