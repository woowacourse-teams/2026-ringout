package com.ringout.api.admin.service;

import com.ringout.api.admin.dto.response.AdminAlarmHistoryResponses.*;
import com.ringout.api.admin.repository.AdminAlarmHistoryRepository;
import com.ringout.api.admin.repository.AdminAlarmRingingRepository;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAlarmHistoryService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final AdminAlarmHistoryRepository histories;
    private final AdminAlarmRingingRepository ringings;
    private final Clock clock;

    public AlarmHistoryPage getHistories(LocalDate date, Long userId, Status status,
            String sortDirection, int page, int size) {
        var now = LocalDateTime.now(clock.withZone(SEOUL));
        var day = date == null ? now.toLocalDate() : date;
        var result = histories.search(day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                userId, status, now, sortDirection, PageRequest.of(page, size));
        var counts = result.isEmpty() ? java.util.Map.<Long, Long>of()
                : ringings.findActiveByOccurrenceIds(result.getContent().stream().map(AlarmOccurrence::getId).toList())
                    .stream().collect(Collectors.groupingBy(ringing -> ringing.getAlarmOccurrence().getId(), Collectors.counting()));
        List<AlarmHistory> content = result.getContent().stream().map(o -> new AlarmHistory(
                o.getId(), o.getOccurrenceUuid(), o.getUser().getId(), o.getUser().getNickname().getValue(),
                o.getClientAlarmId(), o.getScheduledAt(), o.getAlarmTime(), o.getStartedAt(),
                o.getMovementStartedAt(), o.getEndedAt(), status(o, now), o.getEndType(),
                counts.getOrDefault(o.getId(), 0L))).toList();
        return new AlarmHistoryPage(day, SEOUL.getId(), content, page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    private Status status(AlarmOccurrence occurrence, LocalDateTime now) {
        if (occurrence.getEndType() == OccurrenceEndType.FORCE_ENDED) return Status.FORCE_ENDED;
        return Status.valueOf(occurrence.getMovementStatus(now).name());
    }
}
