package com.ringout.api.alarmoccurrence.dto.response;

import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import com.ringout.api.alarmoccurrence.domain.RingingType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record AlarmRingingResponse(
    @Schema(description = "울림 종류 (INITIAL: 최초 울림, REPEAT: 재울림)", example = "INITIAL")
    RingingType type,
    @Schema(description = "재울림 식별자. INITIAL이면 null", example = "b1e2c3d4-0000-4000-8000-000000000001",
        nullable = true)
    String eventId,
    @Schema(description = "울린 시각 (서버 수신 시각)", example = "2026-08-17T06:30:00+09:00")
    OffsetDateTime ringingAt,
    @Schema(description = "해당 울림을 끈 시각. 아직 끄지 않았으면 null", example = "2026-08-17T06:31:02+09:00",
        nullable = true)
    OffsetDateTime dismissedAt
) {

    public static AlarmRingingResponse of(AlarmRinging alarmRinging, ZoneId zoneId) {
        return new AlarmRingingResponse(
            alarmRinging.getType(),
            alarmRinging.getEventId(),
            toOffsetDateTime(alarmRinging.getRingingAt(), zoneId),
            toOffsetDateTime(alarmRinging.getDismissedAt(), zoneId)
        );
    }

    static OffsetDateTime toOffsetDateTime(LocalDateTime dateTime, ZoneId zoneId) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(zoneId).toOffsetDateTime();
    }
}
