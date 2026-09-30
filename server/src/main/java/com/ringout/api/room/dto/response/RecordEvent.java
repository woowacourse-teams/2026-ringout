package com.ringout.api.room.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "모임 회원 활동 기록 이벤트")
public enum RecordEvent {
    ALARM_TRIGGERED,
    ALARM_RINGING,
    ALARM_DISMISSED,
    MOVEMENT_STARTED,
    ARRIVED,
    GAVE_UP
}
