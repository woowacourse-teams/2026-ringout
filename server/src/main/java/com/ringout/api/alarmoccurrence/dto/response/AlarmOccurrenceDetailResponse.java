package com.ringout.api.alarmoccurrence.dto.response;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record AlarmOccurrenceDetailResponse(
    @Schema(description = "서버가 발급한 알람 실행 식별자", example = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f")
    String alarmOccurrenceId,
    @Schema(description = "최초 울림 시각 (서버 수신 시각)", example = "2026-09-23T07:00:02+09:00")
    OffsetDateTime startedAt,
    @Schema(description = "최초 울림과 재울림을 합친 목록. 울림 시각 오름차순이며 첫 항목은 INITIAL")
    List<AlarmRingingResponse> ringings,
    @Schema(description = "목적지 도착 시각 (미션 성공)", example = "2026-09-23T07:48:10+09:00", nullable = true)
    OffsetDateTime arrivedAt,
    @Schema(description = "강제 종료 시각 (미션 실패)", nullable = true)
    OffsetDateTime forceEndedAt
) {

    public static AlarmOccurrenceDetailResponse of(AlarmOccurrence alarmOccurrence, ZoneId zoneId) {
        OffsetDateTime endedAt = AlarmRingingResponse.toOffsetDateTime(alarmOccurrence.getEndedAt(), zoneId);
        return new AlarmOccurrenceDetailResponse(
            alarmOccurrence.getOccurrenceUuid(),
            AlarmRingingResponse.toOffsetDateTime(alarmOccurrence.getStartedAt(), zoneId),
            alarmOccurrence.getRingings().stream()
                .map(alarmRinging -> AlarmRingingResponse.of(alarmRinging, zoneId))
                .toList(),
            alarmOccurrence.getEndType() == OccurrenceEndType.ARRIVED ? endedAt : null,
            alarmOccurrence.getEndType() == OccurrenceEndType.FORCE_ENDED ? endedAt : null
        );
    }
}
