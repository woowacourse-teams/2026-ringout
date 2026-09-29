package com.ringout.api.alarmoccurrence.service;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceEventRequest;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceStartRequest;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceDetailResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceStartResult;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceSummaryResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrencesResponse;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.alarmoccurrence.repository.AlarmRingingRepository;
import com.ringout.api.alarmoccurrence.status.AlarmOccurrenceErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlarmOccurrenceService {

    private static final int MAX_ID_LENGTH = 64;

    private final AlarmOccurrenceRepository alarmOccurrenceRepository;
    private final AlarmRingingRepository alarmRingingRepository;
    private final UserRepository userRepository;

    private final Clock clock;

    @Transactional(readOnly = true)
    public AlarmOccurrencesResponse getAlarmOccurrences(Long userId, LocalDate date) {
        List<AlarmOccurrence> alarmOccurrences = alarmOccurrenceRepository.findActiveByUserIdAndStartedAtBetween(
            userId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        if (alarmOccurrences.isEmpty()) {
            return new AlarmOccurrencesResponse(date, new AlarmOccurrenceSummaryResponse(0, 0), List.of());
        }

        Map<Long, List<AlarmRinging>> ringingsByOccurrenceId = findRingingsByOccurrenceId(alarmOccurrences);
        List<AlarmOccurrenceResponse> occurrences = alarmOccurrences.stream()
            .map(alarmOccurrence -> AlarmOccurrenceResponse.of(alarmOccurrence,
                ringingsByOccurrenceId.getOrDefault(alarmOccurrence.getId(), List.of()), clock.getZone()))
            .toList();

        return new AlarmOccurrencesResponse(date, summarize(occurrences), occurrences);
    }

    @Transactional
    public AlarmOccurrenceStartResult startAlarmOccurrence(Long userId, AlarmOccurrenceStartRequest request) {
        validateStartRequest(request);
        LocalDateTime scheduledAt = toServerDateTime(request.scheduledAt());

        return alarmOccurrenceRepository.findActiveByUserIdAndClientAlarmIdAndScheduledAt(
                userId, request.alarmId(), scheduledAt)
            .map(alarmOccurrence -> new AlarmOccurrenceStartResult(toDetailResponse(alarmOccurrence), false))
            .orElseGet(() -> createAlarmOccurrence(userId, request, scheduledAt));
    }

    @Transactional
    public AlarmOccurrenceDetailResponse recordAlarmOccurrenceEvent(Long userId, String alarmOccurrenceId,
        AlarmOccurrenceEventRequest request) {
        validateAlarmOccurrenceId(alarmOccurrenceId);
        validateEventRequest(request);

        AlarmOccurrence alarmOccurrence = alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(
                alarmOccurrenceId)
            .orElseThrow(() -> new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_NOT_FOUND));
        if (!alarmOccurrence.isOwnedBy(userId)) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_FORBIDDEN);
        }

        applyEvent(alarmOccurrence, request);
        logAlarmOccurrenceEventRecorded(userId, alarmOccurrenceId, request);

        return toDetailResponse(alarmOccurrence);
    }

    private AlarmOccurrenceStartResult createAlarmOccurrence(Long userId, AlarmOccurrenceStartRequest request,
        LocalDateTime scheduledAt) {
        AlarmOccurrence alarmOccurrence = alarmOccurrenceRepository.save(AlarmOccurrence.start(
            userRepository.getReferenceById(userId),
            request.alarmId(),
            scheduledAt,
            scheduledAt.toLocalTime().truncatedTo(ChronoUnit.MINUTES),
            LocalDateTime.now(clock)
        ));
        log.atInfo()
            .addKeyValue("event", "alarm_occurrence_started")
            .addKeyValue("userId", userId)
            .addKeyValue("alarmId", request.alarmId())
            .addKeyValue("alarmOccurrenceId", alarmOccurrence.getOccurrenceUuid())
            .log("알람 실행 시작");

        return new AlarmOccurrenceStartResult(toDetailResponse(alarmOccurrence), true);
    }

    private void applyEvent(AlarmOccurrence alarmOccurrence, AlarmOccurrenceEventRequest request) {
        if (request.eventId() != null) {
            alarmOccurrence.ringRepeat(request.eventId(), LocalDateTime.now(clock));
        }
        if (request.dismissedAt() != null) {
            alarmOccurrence.dismiss(request.eventId(), toServerDateTime(request.dismissedAt()));
        }
        if (request.arrivedAt() != null) {
            alarmOccurrence.end(OccurrenceEndType.ARRIVED, toServerDateTime(request.arrivedAt()));
        }
        if (request.forceEndedAt() != null) {
            alarmOccurrence.end(OccurrenceEndType.FORCE_ENDED, toServerDateTime(request.forceEndedAt()));
        }
    }

    private Map<Long, List<AlarmRinging>> findRingingsByOccurrenceId(List<AlarmOccurrence> alarmOccurrences) {
        List<Long> alarmOccurrenceIds = alarmOccurrences.stream()
            .map(AlarmOccurrence::getId)
            .toList();

        return alarmRingingRepository.findActiveByAlarmOccurrenceIds(alarmOccurrenceIds).stream()
            .collect(Collectors.groupingBy(alarmRinging -> alarmRinging.getAlarmOccurrence().getId()));
    }

    private AlarmOccurrenceSummaryResponse summarize(List<AlarmOccurrenceResponse> occurrences) {
        int alarmCount = (int) occurrences.stream()
            .map(AlarmOccurrenceResponse::alarmId)
            .distinct()
            .count();
        int ringingCount = occurrences.stream()
            .mapToInt(occurrence -> occurrence.ringings().size())
            .sum();

        return new AlarmOccurrenceSummaryResponse(alarmCount, ringingCount);
    }

    private AlarmOccurrenceDetailResponse toDetailResponse(AlarmOccurrence alarmOccurrence) {
        return AlarmOccurrenceDetailResponse.of(alarmOccurrence, clock.getZone());
    }

    private LocalDateTime toServerDateTime(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(clock.getZone()).toLocalDateTime();
    }

    private void logAlarmOccurrenceEventRecorded(Long userId, String alarmOccurrenceId,
        AlarmOccurrenceEventRequest request) {
        log.atInfo()
            .addKeyValue("event", "alarm_occurrence_event_recorded")
            .addKeyValue("userId", userId)
            .addKeyValue("alarmOccurrenceId", alarmOccurrenceId)
            .addKeyValue("eventId", request.eventId())
            .addKeyValue("dismissed", request.dismissedAt() != null)
            .addKeyValue("arrived", request.arrivedAt() != null)
            .addKeyValue("forceEnded", request.forceEndedAt() != null)
            .log("알람 실행 이벤트 저장");
    }

    private void validateStartRequest(AlarmOccurrenceStartRequest request) {
        if (request == null) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_REQUEST_EMPTY);
        }
        if (request.alarmId() == null || request.alarmId().isBlank()) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_ID_REQUIRED);
        }
        if (request.alarmId().length() > MAX_ID_LENGTH) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_ID_TOO_LONG);
        }
        if (request.scheduledAt() == null) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.SCHEDULED_AT_REQUIRED);
        }
    }

    private void validateAlarmOccurrenceId(String alarmOccurrenceId) {
        try {
            if (!UUID.fromString(alarmOccurrenceId).toString().equalsIgnoreCase(alarmOccurrenceId)) {
                throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ID_INVALID);
            }
        } catch (IllegalArgumentException e) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_ID_INVALID);
        }
    }

    private void validateEventRequest(AlarmOccurrenceEventRequest request) {
        if (request == null || request.isEmpty()) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.ALARM_OCCURRENCE_EVENT_EMPTY);
        }
        if (request.eventId() != null && request.eventId().isBlank()) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.EVENT_ID_BLANK);
        }
        if (request.eventId() != null && request.eventId().length() > MAX_ID_LENGTH) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.EVENT_ID_TOO_LONG);
        }
        if (request.arrivedAt() != null && request.forceEndedAt() != null) {
            throw new GeneralException(AlarmOccurrenceErrorStatus.END_TIMES_CONFLICT);
        }
    }
}
