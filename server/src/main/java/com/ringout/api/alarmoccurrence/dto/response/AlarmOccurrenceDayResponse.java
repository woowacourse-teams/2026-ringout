package com.ringout.api.alarmoccurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record AlarmOccurrenceDayResponse(
    @Schema(description = "알람 실행 기록의 기준 날짜. 최초 울림 시각을 서버 시간대로 환산한 날짜", example = "2026-08-17",
        type = "string")
    LocalDate date,
    @Schema(description = "이 날짜에 최초 울림이 발생한 알람 실행 목록. 최초 울림 시각 오름차순")
    List<AlarmOccurrenceResponse> occurrences
) {
}
