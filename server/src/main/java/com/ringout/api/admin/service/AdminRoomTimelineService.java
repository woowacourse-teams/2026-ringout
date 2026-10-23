package com.ringout.api.admin.service;

import com.ringout.api.admin.dto.response.AdminRoomResponses.*;
import com.ringout.api.admin.repository.AdminAlarmRingingRepository;
import com.ringout.api.admin.repository.AdminRoomRepository;
import com.ringout.api.admin.repository.AdminRoomTimelineRepository;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.domain.RingingType;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.dto.response.RecordEvent;
import com.ringout.api.room.status.RoomErrorStatus;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRoomTimelineService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final AdminRoomRepository rooms;
    private final AdminRoomTimelineRepository occurrences;
    private final AdminAlarmRingingRepository ringings;
    private final Clock clock;
    private final com.ringout.api.file.service.ImageFileService images;

    public TimelinePage getTimeline(Long roomId, Long userId, LocalDate date, int page, int size) {
        rooms.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));
        LocalDate day = date == null ? LocalDate.now(clock.withZone(SEOUL)) : date;
        var slice = occurrences.findTimeline(roomId, userId, day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                PageRequest.of(page, size));
        if (slice.isEmpty()) return new TimelinePage(day, SEOUL.getId(), List.of(), page, size, false);
        var byOccurrence = ringings.findActiveByOccurrenceIds(slice.getContent().stream()
                .map(AlarmOccurrence::getId).toList()).stream()
                .collect(Collectors.groupingBy(ringing -> ringing.getAlarmOccurrence().getId()));
        var content = slice.getContent().stream().map(occurrence -> new OccurrenceTimeline(
                occurrence.getId(), occurrence.getUser().getId(), occurrence.getUser().getNickname().getValue(),
                occurrence.getUser().getImage() == null ? null : images.createReadUri(occurrence.getUser().getImage()).toString(),
                occurrence.getScheduledAt(), occurrence.getEndType(),
                events(occurrence, byOccurrence.getOrDefault(occurrence.getId(), List.of())),
                byOccurrence.getOrDefault(occurrence.getId(), List.of()).stream()
                        .map(ringing -> new RingingPeriod(ringing.getId(), ringing.getType(),
                                ringing.getRingingAt(), ringing.getDismissedAt())).toList())).toList();
        return new TimelinePage(day, SEOUL.getId(), content, page, size, slice.hasNext());
    }

    private List<TimelineEvent> events(AlarmOccurrence occurrence, List<AlarmRinging> alarmRingings) {
        List<TimelineEvent> events = new ArrayList<>();
        // The occurrence start is the initial alarm. Do not duplicate its INITIAL ringing.
        add(events, "started", RecordEvent.ALARM_TRIGGERED, occurrence.getStartedAt());
        for (AlarmRinging ringing : alarmRingings) {
            if (ringing.getType() == RingingType.REPEAT) {
                add(events, "ring-" + ringing.getId(), RecordEvent.ALARM_RINGING, ringing.getRingingAt());
            }
            add(events, "dismiss-" + ringing.getId(), RecordEvent.ALARM_DISMISSED, ringing.getDismissedAt());
        }
        add(events, "movement", RecordEvent.MOVEMENT_STARTED, occurrence.getMovementStartedAt());
        if (occurrence.getEndType() == OccurrenceEndType.ARRIVED) {
            add(events, "ended", RecordEvent.ARRIVED, occurrence.getEndedAt());
        } else if (occurrence.getEndType() == OccurrenceEndType.FORCE_ENDED) {
            add(events, "ended", RecordEvent.GAVE_UP, occurrence.getEndedAt());
        }
        return events.stream().sorted(Comparator.comparing(TimelineEvent::occurredAt)
                .thenComparing(TimelineEvent::key)).toList();
    }

    private void add(List<TimelineEvent> events, String key, RecordEvent event, LocalDateTime at) {
        if (at != null) events.add(new TimelineEvent(key, event, at));
    }
}
