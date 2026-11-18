package com.ringout.api.alarm.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AlarmListResponse(
    @Schema(description = "인증된 사용자가 소유한 전체 알람 목록. 알람 시간 오름차순, 같은 시간이면 alarmId 오름차순")
    List<AlarmResponse> alarms
) {

}
