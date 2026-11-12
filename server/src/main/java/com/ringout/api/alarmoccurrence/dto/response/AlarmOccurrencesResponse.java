package com.ringout.api.alarmoccurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.YearMonth;
import java.util.List;

public record AlarmOccurrencesResponse(
    @Schema(description = "조회한 연월", example = "2026-08", type = "string")
    YearMonth month,
    @Schema(description = "조회한 월의 모든 날짜별 알람 실행 기록. 날짜 오름차순")
    List<AlarmOccurrenceDayResponse> days
) {
}
