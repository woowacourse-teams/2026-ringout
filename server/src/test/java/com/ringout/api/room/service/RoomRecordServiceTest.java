package com.ringout.api.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ringout.api.alarmmovement.domain.AlarmMovement;
import com.ringout.api.alarmmovement.repository.AlarmMovementRepository;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.dto.response.MemberRecordResponse;
import com.ringout.api.room.dto.response.RecordEvent;
import com.ringout.api.room.dto.response.RoomRecordsResponse;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.user.domain.User;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RoomRecordServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUserRepository roomUserRepository;

    @Mock
    private AlarmOccurrenceRepository alarmOccurrenceRepository;

    @Mock
    private AlarmMovementRepository alarmMovementRepository;

    private RoomRecordService roomRecordService;

    @BeforeEach
    void setUp() {
        roomRecordService = new RoomRecordService(
            roomRepository, roomUserRepository, alarmOccurrenceRepository, alarmMovementRepository
        );
    }

    @Nested
    class 모임_회원_활동_기록_조회 {

        @Test
        void 해당_활동_날짜에_기록이_없으면_현재_회원과_빈_기록을_반환한다() {
            // given
            LocalDate date = LocalDate.of(2026, 9, 16);
            User requester = userWithId(USER_ID, "아이아티스트님");
            User member = userWithId(2L, "북여서여남여");
            givenCurrentMembers(requester, requester, member);
            given(alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetween(
                ROOM_ID, date.atStartOfDay(), date.plusDays(1).atStartOfDay())).willReturn(List.of());

            // when
            RoomRecordsResponse response = roomRecordService.getRoomRecords(USER_ID, ROOM_ID, date);

            // then
            assertThat(response.memberRecords()).extracting(
                MemberRecordResponse::userId, MemberRecordResponse::nickname, MemberRecordResponse::profileImageUrl
            ).containsExactly(
                tuple(USER_ID, "아이아티스트님", null),
                tuple(2L, "북여서여남여", null)
            );
            assertThat(response.memberRecords()).allSatisfy(memberRecord ->
                assertThat(memberRecord.records()).isEmpty());
            verify(alarmOccurrenceRepository).findActiveByRoomIdAndStartedAtBetween(
                ROOM_ID, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        }

        @Test
        void 회원별로_기록을_그룹화하고_실제_발생_시각_순으로_반환한다() {
            // given
            LocalDate date = LocalDate.of(2026, 9, 16);
            User member = userWithId(USER_ID, "아이아티스트님");
            AlarmOccurrence occurrence = occurrenceWithId(member, 101L, LocalDateTime.of(2026, 9, 16, 7, 0));
            occurrence.dismiss(null, LocalDateTime.of(2026, 9, 16, 7, 0, 30));
            occurrence.ringRepeat("repeat-1", LocalDateTime.of(2026, 9, 16, 7, 5));
            occurrence.dismiss("repeat-1", LocalDateTime.of(2026, 9, 16, 7, 5, 20));
            AlarmMovement movement = AlarmMovement.of(
                occurrence,
                LocalDateTime.of(2026, 9, 16, 7, 7),
                null,
                LocalDateTime.of(2026, 9, 16, 7, 25)
            );
            givenCurrentMembers(member, member);
            given(alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetween(
                ROOM_ID, date.atStartOfDay(), date.plusDays(1).atStartOfDay())).willReturn(List.of(occurrence));
            given(alarmMovementRepository.findActiveByAlarmOccurrenceIn(List.of(occurrence))).willReturn(List.of(movement));

            // when
            RoomRecordsResponse response = roomRecordService.getRoomRecords(USER_ID, ROOM_ID, date);

            // then
            assertThat(response.memberRecords()).hasSize(1);
            MemberRecordResponse memberRecord = response.memberRecords().get(0);
            assertThat(memberRecord.userId()).isEqualTo(USER_ID);
            assertThat(memberRecord.nickname()).isEqualTo("아이아티스트님");
            assertThat(memberRecord.profileImageUrl()).isNull();
            assertThat(memberRecord.records()).extracting(record -> tuple(record.event(), record.occurredAt(), record.count()))
                .containsExactly(
                    tuple(RecordEvent.ALARM_TRIGGERED, OffsetDateTime.parse("2026-09-16T07:00:00+09:00"), null),
                    tuple(RecordEvent.ALARM_DISMISSED, OffsetDateTime.parse("2026-09-16T07:00:30+09:00"), null),
                    tuple(RecordEvent.ALARM_RINGING, OffsetDateTime.parse("2026-09-16T07:05:00+09:00"), 1),
                    tuple(RecordEvent.ALARM_DISMISSED, OffsetDateTime.parse("2026-09-16T07:05:20+09:00"), null),
                    tuple(RecordEvent.MOVEMENT_STARTED, OffsetDateTime.parse("2026-09-16T07:07:00+09:00"), null),
                    tuple(RecordEvent.ARRIVED, OffsetDateTime.parse("2026-09-16T07:25:00+09:00"), null)
                );
        }

        @Test
        void 자정을_넘긴_후속_이벤트도_최초_활동_날짜의_기록으로_반환한다() {
            // given
            LocalDate activityDate = LocalDate.of(2026, 9, 16);
            User member = userWithId(USER_ID, "야간러너");
            AlarmOccurrence occurrence = occurrenceWithId(member, 101L, LocalDateTime.of(2026, 9, 16, 23, 50));
            AlarmMovement movement = AlarmMovement.of(
                occurrence,
                LocalDateTime.of(2026, 9, 16, 23, 55),
                null,
                LocalDateTime.of(2026, 9, 17, 0, 20)
            );
            givenCurrentMembers(member, member);
            given(alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetween(
                ROOM_ID, activityDate.atStartOfDay(), activityDate.plusDays(1).atStartOfDay()))
                .willReturn(List.of(occurrence));
            given(alarmMovementRepository.findActiveByAlarmOccurrenceIn(List.of(occurrence))).willReturn(List.of(movement));

            // when
            RoomRecordsResponse response = roomRecordService.getRoomRecords(USER_ID, ROOM_ID, activityDate);

            // then
            assertThat(response.memberRecords().get(0).records())
                .extracting(record -> tuple(record.event(), record.occurredAt()))
                .contains(tuple(RecordEvent.ARRIVED, OffsetDateTime.parse("2026-09-17T00:20:00+09:00")));
        }
    }

    private void givenCurrentMembers(User requester, User... members) {
        Room room = mock(Room.class);
        List<RoomUser> roomUsers = java.util.stream.Stream.of(members)
            .map(member -> RoomUser.of(member, room))
            .toList();
        RoomUser requesterRoomUser = roomUsers.stream()
            .filter(roomUser -> roomUser.getUser().getId().equals(requester.getId()))
            .findFirst()
            .orElseThrow();
        given(roomRepository.findActiveById(ROOM_ID)).willReturn(Optional.of(room));
        given(roomUserRepository.findActiveByRoomIdAndUserId(ROOM_ID, USER_ID))
            .willReturn(Optional.of(requesterRoomUser));
        given(roomUserRepository.findActiveByRoomId(ROOM_ID)).willReturn(roomUsers);
    }

    private User userWithId(Long userId, String nickname) {
        User user = User.register(SocialProvider.KAKAO, "provider-" + userId, null,
            LocalDateTime.of(2026, 1, 1, 0, 0));
        user.changeNickname(nickname);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private AlarmOccurrence occurrenceWithId(User user, Long occurrenceId, LocalDateTime startedAt) {
        AlarmOccurrence occurrence = AlarmOccurrence.start(
            user,
            "alarm-" + occurrenceId,
            startedAt,
            LocalTime.from(startedAt),
            startedAt
        );
        ReflectionTestUtils.setField(occurrence, "id", occurrenceId);
        return occurrence;
    }
}
