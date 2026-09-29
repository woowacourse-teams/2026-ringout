package com.ringout.api.alarmoccurrence.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AlarmOccurrenceSummaryResponse(
    @Schema(description = "이 날짜에 실행 기록이 있는 서로 다른 알람의 수", example = "1")
    int alarmCount,
    @Schema(description = "이 날짜 전체 실행의 울림 횟수 합계 (최초 울림 + 재울림)", example = "5")
    int ringingCount
) {
}
