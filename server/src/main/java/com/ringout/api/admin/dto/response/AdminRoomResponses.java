package com.ringout.api.admin.dto.response;

import com.ringout.api.room.domain.ActivityDay;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class AdminRoomResponses {
    private AdminRoomResponses() {}

    public record RoomStats(LocalDate date, String timeZone, LocalDateTime updatedAt,
            long totalRooms, long createdToday, long activeToday) {}

    public record Host(Long id, String nickname) {}

    public record RoomSummary(Long id, String name, String imageUrl, Host host,
            long memberCount, List<ActivityDay> activityDays, LocalTime activityTime) {}

    public record RoomPage(List<RoomSummary> content, int page, int size,
            long totalElements, int totalPages) {}

    public record RoomDetail(Long id, String name, String description, String imageUrl,
            Host host, long memberCount, List<ActivityDay> activityDays,
            LocalTime activityTime, LocalDateTime createdAt, LocalDateTime latestActivityAt) {}

    public enum RecordStatus {
        ARRIVED, FORCE_ENDED, UNCONFIRMED, NO_RECORD
    }

    public record MemberRecord(Long userId, String nickname, String imageUrl, boolean host,
            LocalDateTime joinedAt,
            Long occurrenceId, int occurrenceCount, LocalDateTime alarmAt,
            LocalDateTime movementStartedAt, LocalDateTime endedAt,
            Long durationSeconds, RecordStatus status) {}

    public record TimelineEvent(String key, com.ringout.api.room.dto.response.RecordEvent event,
            LocalDateTime occurredAt) {}

    public record RingingPeriod(Long id, com.ringout.api.alarmoccurrence.domain.RingingType type,
            LocalDateTime ringingAt, LocalDateTime dismissedAt) {}

    public record OccurrenceTimeline(Long occurrenceId, Long userId, String nickname, String imageUrl,
            LocalDateTime scheduledAt, com.ringout.api.alarmoccurrence.domain.OccurrenceEndType endType,
            List<TimelineEvent> events, List<RingingPeriod> ringings) {}

    public record TimelinePage(LocalDate date, String timeZone, List<OccurrenceTimeline> content,
            int page, int size, boolean hasNext) {}

    public record Records(LocalDate date, String timeZone, long totalMembers,
            long recordedMembers, long arrivedMembers, long unconfirmedMembers,
            long totalElements, List<MemberRecord> content) {}
}
