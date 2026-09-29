package com.ringout.api.alarmoccurrence.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

public record AlarmOccurrenceResponse(
    @Schema(description = "클라이언트 알람 식별자", example = "8f2c1d4e-7a9b-4c3d-9e1f-2a3b4c5d6e7f")
    String alarmId,
    @Schema(description = "알람 실행 식별자", example = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f")
    String alarmOccurrenceId,
    @Schema(description = "알람의 설정 시각", example = "06:30", type = "string")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm")
    LocalTime alarmTime,
    @Schema(description = "최초 울림과 재울림을 합친 목록. 울림 시각 오름차순")
    List<AlarmRingingResponse> ringings,
    @Schema(description = "종료 종류 (ARRIVED: 목적지 도착, FORCE_ENDED: 강제 종료). 둘 다 없으면 null",
        example = "ARRIVED", nullable = true)
    OccurrenceEndType endType,
    @Schema(description = "도착 또는 강제 종료 시각", example = "2026-08-17T06:53:10+09:00", nullable = true)
    OffsetDateTime endedAt
) {

    public static AlarmOccurrenceResponse of(AlarmOccurrence alarmOccurrence, List<AlarmRinging> alarmRingings,
        ZoneId zoneId) {
        return new AlarmOccurrenceResponse(
            alarmOccurrence.getClientAlarmId(),
            alarmOccurrence.getOccurrenceUuid(),
            alarmOccurrence.getAlarmTime(),
            alarmRingings.stream()
                .map(alarmRinging -> AlarmRingingResponse.of(alarmRinging, zoneId))
                .toList(),
            alarmOccurrence.getEndType(),
            AlarmRingingResponse.toOffsetDateTime(alarmOccurrence.getEndedAt(), zoneId)
        );
    }
}
