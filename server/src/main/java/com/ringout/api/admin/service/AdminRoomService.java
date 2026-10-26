package com.ringout.api.admin.service;

import com.ringout.api.admin.dto.response.AdminRoomResponses.*;
import com.ringout.api.admin.repository.AdminRoomRepository;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.service.ImageFileService;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.status.RoomErrorStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminRoomService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Comparator<AlarmOccurrence> OCCURRENCE_ORDER = Comparator
            .comparing(AlarmOccurrence::getStartedAt).thenComparing(AlarmOccurrence::getId);

    private final AdminRoomRepository rooms;
    private final AlarmOccurrenceRepository occurrences;
    private final ImageFileService images;
    private final Clock clock;

    public RoomStats getStats() {
        var now = java.time.ZonedDateTime.now(clock.withZone(SEOUL));
        var start = now.toLocalDate().atStartOfDay();
        var end = start.plusDays(1);
        return new RoomStats(now.toLocalDate(), SEOUL.getId(), now.toLocalDateTime(),
                rooms.countCurrentRooms(), rooms.countCreatedBetween(start, end),
                rooms.countActiveBetween(start, end));
    }

    public RoomPage getRooms(String search, int page, int size) {
        String keyword = search == null ? "" : search.trim();
        String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%")
                .replace("_", "!_") + "%";
        var result = rooms.search(keyword, pattern,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id")));
        var counts = memberCounts(result.getContent().stream().map(Room::getId).toList());
        var content = result.getContent().stream().map(room -> new RoomSummary(
                room.getId(), room.getName(), imageUrl(room.getImage()), host(room),
                counts.getOrDefault(room.getId(), 0L), room.getActivityDays(), room.getActivityTime()))
                .toList();
        return new RoomPage(content, page, size, result.getTotalElements(), result.getTotalPages());
    }

    public RoomDetail getRoom(Long roomId) {
        Room room = requireRoom(roomId);
        return new RoomDetail(room.getId(), room.getName(), room.getDescription(), imageUrl(room.getImage()),
                host(room), memberCounts(List.of(roomId)).getOrDefault(roomId, 0L),
                room.getActivityDays(), room.getActivityTime(), room.getCreated_at(), room.getLatestActivityAt());
    }

    public Records getRecords(Long roomId, LocalDate date, String search, RecordStatus status) {
        Room room = requireRoom(roomId);
        LocalDate activityDate = date == null ? LocalDate.now(clock.withZone(SEOUL)) : date;
        var byMember = occurrences.findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
                roomId, activityDate.atStartOfDay(), activityDate.plusDays(1).atStartOfDay())
                .stream().collect(Collectors.groupingBy(occurrence -> occurrence.getUser().getId()));
        var allRecords = rooms.findMembers(roomId).stream()
                .map(member -> memberRecord(room, member, byMember.getOrDefault(member.getUser().getId(), List.of())))
                .toList();
        String keyword = search == null ? "" : search.trim();
        var content = allRecords.stream()
                .filter(record -> record.nickname().contains(keyword))
                .filter(record -> status == null || record.status() == status)
                .toList();
        // Summary counts always cover the full roster, independently of display filters.
        return new Records(activityDate, SEOUL.getId(), allRecords.size(),
                allRecords.stream().filter(record -> record.status() != RecordStatus.NO_RECORD).count(),
                allRecords.stream().filter(record -> record.status() == RecordStatus.ARRIVED).count(),
                allRecords.stream().filter(record -> record.status() == RecordStatus.UNCONFIRMED).count(),
                content.size(), content);
    }

    private MemberRecord memberRecord(Room room, RoomUser member, List<AlarmOccurrence> records) {
        var user = member.getUser();
        AlarmOccurrence latest = records.stream().max(OCCURRENCE_ORDER).orElse(null);
        if (latest == null) {
            return new MemberRecord(user.getId(), user.getNickname().getValue(), imageUrl(user.getImage()),
                    room.isHostedBy(user.getId()), member.getCreated_at(), null, 0, null, null, null, null, RecordStatus.NO_RECORD);
        }
        RecordStatus status = recordStatus(latest);
        Long duration = latest.getMovementStartedAt() != null && latest.getEndedAt() != null
                && !latest.getEndedAt().isBefore(latest.getMovementStartedAt())
                ? Duration.between(latest.getMovementStartedAt(), latest.getEndedAt()).getSeconds() : null;
        return new MemberRecord(user.getId(), user.getNickname().getValue(), imageUrl(user.getImage()),
                room.isHostedBy(user.getId()), member.getCreated_at(), latest.getId(), records.size(), latest.getStartedAt(),
                latest.getMovementStartedAt(), latest.getEndedAt(), duration, status);
    }

    private RecordStatus recordStatus(AlarmOccurrence occurrence) {
        if (occurrence.getEndedAt() == null) return RecordStatus.UNCONFIRMED;
        if (occurrence.getEndType() == OccurrenceEndType.ARRIVED) return RecordStatus.ARRIVED;
        if (occurrence.getEndType() == OccurrenceEndType.FORCE_ENDED) return RecordStatus.FORCE_ENDED;
        return RecordStatus.UNCONFIRMED;
    }

    private Room requireRoom(Long roomId) {
        return rooms.findByIdAndDeletedAtIsNull(roomId)
                .orElseThrow(() -> new GeneralException(RoomErrorStatus.ROOM_NOT_FOUND));
    }

    private Map<Long, Long> memberCounts(List<Long> roomIds) {
        if (roomIds.isEmpty()) return Map.of();
        return rooms.countMembers(roomIds).stream().collect(Collectors.toMap(
                AdminRoomRepository.MemberCount::getRoomId, AdminRoomRepository.MemberCount::getMemberCount));
    }

    private Host host(Room room) {
        return new Host(room.getHostUser().getId(), room.getHostUser().getNickname().getValue());
    }

    private String imageUrl(ImageFile image) {
        return image == null ? null : images.createReadUri(image).toString();
    }
}
