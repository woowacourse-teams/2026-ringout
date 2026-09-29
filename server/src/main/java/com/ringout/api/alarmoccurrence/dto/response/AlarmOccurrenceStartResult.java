package com.ringout.api.alarmoccurrence.dto.response;

public record AlarmOccurrenceStartResult(
    AlarmOccurrenceDetailResponse response,
    boolean created
) {
}
