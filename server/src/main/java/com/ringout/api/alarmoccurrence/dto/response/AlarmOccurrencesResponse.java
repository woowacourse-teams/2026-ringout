package com.ringout.api.alarmoccurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

public record AlarmOccurrencesResponse(
    @Schema(description = "조회한 날짜", example = "2026-08-17", type = "string")
    LocalDate date,
    @Schema(description = "조회한 날짜의 알람 사용 요약")
    AlarmOccurrenceSummaryResponse summary,
    @Schema(description = "알람 실행 목록. 최초 울림 시각 오름차순")
    List<AlarmOccurrenceResponse> occurrences
) {
}
