package com.ringout.api.admin.dto.response;

import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class AdminAlarmHistoryResponses {
    private AdminAlarmHistoryResponses() {}

    public enum Status { ALARM_TRIGGERED, MOVEMENT_STARTED, MOVING, ARRIVED, FORCE_ENDED }

    public record AlarmHistory(Long id, String occurrenceUuid, Long userId, String nickname,
            String clientAlarmId, LocalDateTime scheduledAt, LocalTime alarmTime,
            LocalDateTime startedAt, LocalDateTime movementStartedAt, LocalDateTime endedAt,
            Status status, OccurrenceEndType endType, long ringingCount) {}

    public record AlarmHistoryPage(LocalDate date, String timeZone, List<AlarmHistory> content,
            int page, int size, long totalElements, int totalPages) {}
}
