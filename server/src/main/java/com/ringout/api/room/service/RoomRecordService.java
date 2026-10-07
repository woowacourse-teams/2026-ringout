package com.ringout.api.room.service;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.domain.RingingType;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.service.ImageFileService;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.dto.response.ActivityRecordResponse;
import com.ringout.api.room.dto.response.MemberRecordResponse;
import com.ringout.api.room.dto.response.RecordEvent;
import com.ringout.api.room.dto.response.RoomRecordsResponse;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.status.RecordErrorStatus;
import com.ringout.api.room.status.RoomErrorStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomRecordService {

    private static final ZoneId KOREA_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final RoomRepository roomRepository;
    private final RoomUserRepository roomUserRepository;
    private final AlarmOccurrenceRepository alarmOccurrenceRepository;
    private final ImageFileService imageFileService;

    @Transactional(readOnly = true)
    public RoomRecordsResponse getRoomRecords(Long userId, Long roomId, String date) {
        return getRoomRecords(userId, roomId, parseActivityDate(date));
    }

    @Transactional(readOnly = true)
    public RoomRecordsResponse getRoomRecords(Long userId, Long roomId, LocalDate date) {
        validateRoomId(roomId);
        validateCurrentRoomMember(userId, roomId);

        List<RoomUser> roomUsers = roomUserRepository.findActiveByRoomId(roomId);
        List<AlarmOccurrence> occurrences = alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetween(
            roomId, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        Map<Long, List<AlarmOccurrence>> occurrencesByMemberId = groupOccurrencesByMemberId(occurrences);
        List<MemberRecordResponse> memberRecords = roomUsers.stream()
            .map(roomUser -> toMemberRecordResponse(roomUser, occurrencesByMemberId))
            .toList();

        return new RoomRecordsResponse(memberRecords);
    }

    private LocalDate parseActivityDate(String date) {
        if (date == null) {
            throw new GeneralException(RecordErrorStatus.RECORD_DATE_INVALID);
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw new GeneralException(RecordErrorStatus.RECORD_DATE_INVALID);
        }
    }

    private void validateRoomId(Long roomId) {
        if (roomId == null || roomId <= 0) {
            throw new GeneralException(RoomErrorStatus.ROOM_ID_INVALID);
        }
    }

    private void validateCurrentRoomMember(Long userId, Long roomId) {
        roomRepository.findActiveById(roomId)
            .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));
        if (roomUserRepository.findActiveByRoomIdAndUserId(roomId, userId).isEmpty()) {
            throw new GeneralException(RecordErrorStatus.RECORD_FORBIDDEN);
        }
    }

    private Map<Long, List<AlarmOccurrence>> groupOccurrencesByMemberId(List<AlarmOccurrence> occurrences) {
        return occurrences.stream()
            .collect(Collectors.groupingBy(occurrence -> occurrence.getUser().getId()));
    }

    private MemberRecordResponse toMemberRecordResponse(RoomUser roomUser,
        Map<Long, List<AlarmOccurrence>> occurrencesByMemberId) {
        List<ActivityRecordResponse> records = occurrencesByMemberId
            .getOrDefault(roomUser.getUser().getId(), List.of())
            .stream()
            .flatMap(occurrence -> toActivityRecords(occurrence).stream())
            .sorted(Comparator.comparing(ActivityRecordResponse::occurredAt))
            .toList();

        String profileImageUrl = resolveProfileImageUrl(roomUser);
        return new MemberRecordResponse(
            roomUser.getUser().getId(),
            roomUser.getUser().getNickname().getValue(),
            profileImageUrl,
            records
        );
    }

    private String resolveProfileImageUrl(RoomUser roomUser) {
        ImageFile profileImage = roomUser.getUser().getImage();
        if (profileImage == null) {
            return null;
        }

        return imageFileService.createReadUri(profileImage).toString();
    }

    private List<ActivityRecordResponse> toActivityRecords(AlarmOccurrence occurrence) {
        List<ActivityRecordResponse> records = new ArrayList<>(toAlarmRecords(occurrence));
        records.addAll(toMovementRecords(occurrence));
        return records;
    }

    private List<ActivityRecordResponse> toAlarmRecords(AlarmOccurrence occurrence) {
        List<ActivityRecordResponse> records = new ArrayList<>();
        int repeatCount = 0;
        for (AlarmRinging ringing : occurrence.getRingings()) {
            Integer currentRepeatCount = ringing.getType() == RingingType.INITIAL ? null : ++repeatCount;
            records.add(toAlarmRingingRecord(ringing, currentRepeatCount));
            addRecordIfOccurred(records, RecordEvent.ALARM_DISMISSED, ringing.getDismissedAt());
        }
        return records;
    }

    private ActivityRecordResponse toAlarmRingingRecord(AlarmRinging ringing, Integer repeatCount) {
        if (ringing.getType() == RingingType.INITIAL) {
            return record(RecordEvent.ALARM_TRIGGERED, ringing.getRingingAt(), null);
        }
        return record(RecordEvent.ALARM_RINGING, ringing.getRingingAt(), repeatCount);
    }

    private List<ActivityRecordResponse> toMovementRecords(AlarmOccurrence occurrence) {
        List<ActivityRecordResponse> records = new ArrayList<>();
        addRecordIfOccurred(records, RecordEvent.MOVEMENT_STARTED, occurrence.getMovementStartedAt());
        if (occurrence.getEndType() == OccurrenceEndType.ARRIVED) {
            addRecordIfOccurred(records, RecordEvent.ARRIVED, occurrence.getEndedAt());
        }
        if (occurrence.getEndType() == OccurrenceEndType.FORCE_ENDED) {
            addRecordIfOccurred(records, RecordEvent.GAVE_UP, occurrence.getEndedAt());
        }
        return records;
    }

    private void addRecordIfOccurred(
        List<ActivityRecordResponse> records,
        RecordEvent event,
        LocalDateTime occurredAt
    ) {
        if (occurredAt != null) {
            records.add(record(event, occurredAt, null));
        }
    }

    private ActivityRecordResponse record(RecordEvent event, LocalDateTime occurredAt, Integer count) {
        return new ActivityRecordResponse(event,
            OffsetDateTime.of(occurredAt, KOREA_ZONE_ID.getRules().getOffset(occurredAt)),
            count);
    }
}
