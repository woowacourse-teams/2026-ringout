package com.ringout.api.alarmmovement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ringout.api.alarmmovement.domain.MovementAction;
import com.ringout.api.alarmmovement.domain.MovementStatus;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementResponse;
import com.ringout.api.alarmmovement.dto.response.MemberMovementsResponse;
import com.ringout.api.alarmmovement.status.AlarmMovementErrorStatus;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import com.ringout.api.alarmoccurrence.repository.AlarmOccurrenceRepository;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.file.domain.ImageFile;
import com.ringout.api.file.service.ImageFileService;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.room.service.RoomActivityService;
import com.ringout.api.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.net.URI;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlarmMovementServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final String ALARM_OCCURRENCE_ID = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f";
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-29T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUserRepository roomUserRepository;

    @Mock
    private AlarmOccurrenceRepository alarmOccurrenceRepository;

    @Mock
    private RoomActivityService roomActivityService;

    @Mock
    private ImageFileService imageFileService;

    private AlarmMovementService alarmMovementService;

    @BeforeEach
    void setUp() {
        alarmMovementService = new AlarmMovementService(
            roomRepository, roomUserRepository, alarmOccurrenceRepository, roomActivityService,
            imageFileService, CLOCK
        );
    }

    @Nested
    class 모임_회원_알람_실행_이동_행동_처리 {

        @ParameterizedTest
        @CsvSource({"START_MOVEMENT, MOVEMENT_STARTED", "GIVE_UP, GAVE_UP", "ARRIVE, ARRIVED"})
        void 알람_실행의_이동_상태를_변경하고_반환한다(MovementAction action, MovementStatus expectedStatus) {
            // given
            givenCurrentMember();
            AlarmOccurrence alarmOccurrence = alarmOccurrenceWith(userWithId(USER_ID, "요청자"), 11L);
            given(alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(ALARM_OCCURRENCE_ID))
                .willReturn(Optional.of(alarmOccurrence));
            AlarmMovementRequest request = new AlarmMovementRequest(ALARM_OCCURRENCE_ID, action);

            // when
            AlarmMovementResponse response = alarmMovementService.changeMovement(USER_ID, ROOM_ID, request);

            // then
            assertThat(response.status()).isEqualTo(expectedStatus);
            assertThat(alarmOccurrence.getMovementStatus(NOW)).isEqualTo(expectedStatus);
            verify(alarmOccurrenceRepository).findActiveByOccurrenceUuidForUpdate(ALARM_OCCURRENCE_ID);
            verify(roomActivityService).recordMovementActivity(any(Room.class), eq(NOW));
        }
    }

    @Nested
    class 모임_회원_이동_상태_조회 {

        @Test
        void 현재_회원의_상태를_한글_영어_기타_문자_순으로_반환한다() {
            // given
            Room room = roomWithId(ROOM_ID);
            User requester = userWithId(USER_ID, "요청자");
            User idleMember = userWithId(2L, "가나다");
            User gaveUpMember = userWithId(3L, "나비");
            User triggeredMember = userWithId(4L, "Alpha");
            User startedMember = userWithId(5L, "Bravo");
            User movingMember = userWithId(6L, "charlie");
            User arrivedMember = userWithId(7L, "123");
            ImageFile startedMemberProfileImage = ImageFile.from("images/profiles/started-member.png");
            URI startedMemberProfileImageUri = URI.create("https://example.com/started-member.png?signature=test");
            startedMember.changeProfileImage(startedMemberProfileImage);
            List<AlarmOccurrence> alarmOccurrences = List.of(
                alarmOccurrenceWith(arrivedMember, 11L),
                alarmOccurrenceWith(movingMember, 12L),
                alarmOccurrenceWith(startedMember, 13L),
                alarmOccurrenceWith(triggeredMember, 14L),
                alarmOccurrenceWith(gaveUpMember, 15L)
            );
            alarmOccurrences.get(0).changeMovement(MovementAction.ARRIVE, NOW.minusMinutes(1));
            alarmOccurrences.get(1).changeMovement(MovementAction.START_MOVEMENT, NOW.minusMinutes(2));
            alarmOccurrences.get(2).changeMovement(MovementAction.START_MOVEMENT, NOW.minusMinutes(1));
            alarmOccurrences.get(4).changeMovement(MovementAction.GIVE_UP, NOW.minusMinutes(1));
            givenCurrentMember();
            given(roomUserRepository.findActiveByRoomId(ROOM_ID)).willReturn(List.of(
                RoomUser.of(arrivedMember, room),
                RoomUser.of(movingMember, room),
                RoomUser.of(startedMember, room),
                RoomUser.of(triggeredMember, room),
                RoomUser.of(gaveUpMember, room),
                RoomUser.of(idleMember, room)
            ));
            given(alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
                ROOM_ID, NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay()))
                .willReturn(alarmOccurrences);
            given(imageFileService.createReadUri(startedMemberProfileImage)).willReturn(startedMemberProfileImageUri);

            // when
            MemberMovementsResponse response = alarmMovementService.getMemberMovements(USER_ID, ROOM_ID);

            // then
            assertThat(response.members()).extracting(MemberMovementResponse::userId)
                .containsExactly(2L, 3L, 4L, 5L, 6L, 7L);
            assertThat(response.members()).extracting(MemberMovementResponse::nickname)
                .containsExactly("가나다", "나비", "Alpha", "Bravo", "charlie", "123");
            assertThat(response.members()).extracting(MemberMovementResponse::status)
                .containsExactly(
                    MovementStatus.IDLE,
                    MovementStatus.GAVE_UP,
                    MovementStatus.ALARM_TRIGGERED,
                    MovementStatus.MOVEMENT_STARTED,
                    MovementStatus.MOVING,
                    MovementStatus.ARRIVED
                );
            assertThat(response.members()).extracting(MemberMovementResponse::profileImageUrl)
                .containsExactly(null, null, null, startedMemberProfileImageUri.toString(), null, null);
            verify(roomUserRepository).findActiveByRoomId(ROOM_ID);
            verify(imageFileService).createReadUri(startedMemberProfileImage);
            verify(alarmOccurrenceRepository).findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
                ROOM_ID, NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay());
        }

        @Test
        void 한_회원에게_알람_실행_이력이_여러_개면_실제_시작_시각이_가장_최근인_실행의_상태를_반환한다() {
            // given
            Room room = roomWithId(ROOM_ID);
            User requester = userWithId(USER_ID, "요청자");
            AlarmOccurrence earlierStartedOccurrence = alarmOccurrenceWith(requester, 12L, NOW.minusMinutes(30));
            AlarmOccurrence laterStartedOccurrence = alarmOccurrenceWith(requester, 11L, NOW.minusMinutes(5));
            earlierStartedOccurrence.changeMovement(MovementAction.ARRIVE, NOW);
            givenCurrentMember();
            given(roomUserRepository.findActiveByRoomId(ROOM_ID)).willReturn(List.of(RoomUser.of(requester, room)));
            given(alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
                ROOM_ID, NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay()))
                .willReturn(List.of(laterStartedOccurrence, earlierStartedOccurrence));

            // when
            MemberMovementsResponse response = alarmMovementService.getMemberMovements(USER_ID, ROOM_ID);

            // then
            assertThat(response.members()).extracting(MemberMovementResponse::status)
                .containsExactly(MovementStatus.ALARM_TRIGGERED);
        }

        @Test
        void 오늘_울린_알람_실행이_없으면_이전_날_도착한_회원도_IDLE을_반환한다() {
            // given
            Room room = roomWithId(ROOM_ID);
            User requester = userWithId(USER_ID, "요청자");
            AlarmOccurrence yesterdayOccurrence = alarmOccurrenceWith(requester, 11L, NOW.minusDays(1));
            yesterdayOccurrence.changeMovement(MovementAction.ARRIVE, NOW.minusDays(1).plusMinutes(1));
            givenCurrentMember();
            given(roomUserRepository.findActiveByRoomId(ROOM_ID)).willReturn(List.of(RoomUser.of(requester, room)));
            given(alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
                ROOM_ID, NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay()))
                .willReturn(List.of());

            // when
            MemberMovementsResponse response = alarmMovementService.getMemberMovements(USER_ID, ROOM_ID);

            // then
            assertThat(response.members()).extracting(MemberMovementResponse::status)
                .containsExactly(MovementStatus.IDLE);
            verify(alarmOccurrenceRepository).findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
                ROOM_ID, NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay());
        }
    }

    @Nested
    class 모임_회원_이동_상태_조회_권한 {

        @Test
        void 인증된_사용자가_아니면_조회할_수_없다() {
            // given
            Long userId = null;

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.getMemberMovements(userId, ROOM_ID));

            // then
            assertError(thrown, 401, "ROOM401", "인증되지 않은 사용자입니다.");
            verifyNoInteractions(roomRepository, roomUserRepository, alarmOccurrenceRepository);
        }

        @Test
        void 존재하지_않거나_삭제된_모임방은_조회할_수_없다() {
            // given
            given(roomRepository.findActiveById(ROOM_ID)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.getMemberMovements(USER_ID, ROOM_ID));

            // then
            assertError(thrown, 404, "ROOM404", "존재하지 않는 모임 방입니다.");
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 모임방_ID가_양수가_아니면_조회할_수_없다() {
            // given
            Long roomId = 0L;

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.getMemberMovements(USER_ID, roomId));

            // then
            assertError(thrown, 400, "ROOM400", "모임 방 ID는 양수여야 합니다.");
            verifyNoInteractions(roomRepository, roomUserRepository, alarmOccurrenceRepository);
        }

        @Test
        void 현재_모임_회원이_아니면_조회할_수_없다() {
            // given
            given(roomRepository.findActiveById(ROOM_ID)).willReturn(Optional.of(roomWithId(ROOM_ID)));
            given(roomUserRepository.findActiveByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.getMemberMovements(USER_ID, ROOM_ID));

            // then
            assertError(thrown, 403, "ROOM403", "모임 회원 상태를 조회할 권한이 없습니다.");
            verifyNoInteractions(alarmOccurrenceRepository);
        }

        @Test
        void 알람_실행에_별도_이동_기록이_없어도_알람_실행_기준_상태를_반환한다() {
            // given
            Room room = roomWithId(ROOM_ID);
            User requester = userWithId(USER_ID, "요청자");
            AlarmOccurrence alarmOccurrence = alarmOccurrenceWith(requester, 11L);
            givenCurrentMember();
            given(roomUserRepository.findActiveByRoomId(ROOM_ID)).willReturn(List.of(RoomUser.of(requester, room)));
            given(alarmOccurrenceRepository.findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
                ROOM_ID, NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay()))
                .willReturn(List.of(alarmOccurrence));

            // when
            MemberMovementsResponse response = alarmMovementService.getMemberMovements(USER_ID, ROOM_ID);

            // then
            assertThat(response.members()).extracting(MemberMovementResponse::status)
                .containsExactly(MovementStatus.ALARM_TRIGGERED);
        }

    }

    @Nested
    class 모임_회원_권한_검증 {

        @Test
        void 모임의_현재_회원이_아니면_알람_이동_상태를_변경할_수_없다() {
            // given
            given(roomRepository.findActiveById(ROOM_ID)).willReturn(Optional.of(roomWithId(ROOM_ID)));
            given(roomUserRepository.findActiveByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
            AlarmMovementRequest request = new AlarmMovementRequest(ALARM_OCCURRENCE_ID, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertError(thrown, 403, "MOVEMENT403", "해당 모임의 회원이 아닙니다.");
            verifyNoInteractions(alarmOccurrenceRepository);
        }
    }

    @Nested
    class 알람_실행_검증 {

        @Test
        void 존재하지_않는_알람이면_알람_이동_상태를_변경할_수_없다() {
            // given
            givenCurrentMember();
            given(alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(ALARM_OCCURRENCE_ID))
                .willReturn(Optional.empty());
            AlarmMovementRequest request = new AlarmMovementRequest(ALARM_OCCURRENCE_ID, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertError(thrown, 404, "ALARM404", "존재하지 않는 알람 입니다.");
        }

        @Test
        void 알람_실행에_별도_이동_기록이_없어도_이동_시작을_처리한다() {
            // given
            givenCurrentMember();
            AlarmOccurrence alarmOccurrence = alarmOccurrenceWith(userWithId(USER_ID, "요청자"), 11L);
            given(alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(ALARM_OCCURRENCE_ID))
                .willReturn(Optional.of(alarmOccurrence));
            AlarmMovementRequest request = new AlarmMovementRequest(ALARM_OCCURRENCE_ID, MovementAction.START_MOVEMENT);

            // when
            AlarmMovementResponse response = alarmMovementService.changeMovement(USER_ID, ROOM_ID, request);

            // then
            assertThat(response.status()).isEqualTo(MovementStatus.MOVEMENT_STARTED);
            verify(roomActivityService).recordMovementActivity(any(Room.class), eq(NOW));
        }
    }

    @Nested
    class 이동_요청_형식_검증 {

        @Test
        void 요청_본문이_없으면_요청_본문_누락_오류를_반환한다() {
            // given
            AlarmMovementRequest request = null;

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_REQUEST_EMPTY));
            verifyNoInteractions(roomRepository, roomUserRepository, alarmOccurrenceRepository);
        }

        @Test
        void 알람_실행_ID가_없으면_알람_실행_ID_누락_오류를_반환한다() {
            // given
            AlarmMovementRequest request = new AlarmMovementRequest(null, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_ALARM_OCCURRENCE_ID_REQUIRED));
            verifyNoInteractions(roomRepository, roomUserRepository, alarmOccurrenceRepository);
        }

        @Test
        void 알람_실행_ID가_UUID_형식이_아니면_알람_실행_ID_형식_오류를_반환한다() {
            // given
            AlarmMovementRequest request = new AlarmMovementRequest("not-a-uuid", MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_ALARM_OCCURRENCE_ID_INVALID));
            verifyNoInteractions(roomRepository, roomUserRepository, alarmOccurrenceRepository);
        }

        @Test
        void 이동_행동이_없으면_이동_행동_누락_오류를_반환한다() {
            // given
            AlarmMovementRequest request = new AlarmMovementRequest(ALARM_OCCURRENCE_ID, null);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_ACTION_REQUIRED));
            verifyNoInteractions(roomRepository, roomUserRepository, alarmOccurrenceRepository);
        }
    }

    @Nested
    class 알람_실행_소유_권한_검증 {

        @Test
        void 다른_사용자의_알람_실행은_이동_상태를_변경할_수_없다() {
            // given
            givenCurrentMember();
            AlarmOccurrence alarmOccurrence = mock(AlarmOccurrence.class);
            given(alarmOccurrenceRepository.findActiveByOccurrenceUuidForUpdate(ALARM_OCCURRENCE_ID))
                .willReturn(Optional.of(alarmOccurrence));
            given(alarmOccurrence.isOwnedBy(USER_ID)).willReturn(false);
            AlarmMovementRequest request = new AlarmMovementRequest(ALARM_OCCURRENCE_ID, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertError(thrown, 403, "MOVEMENT403", "해당 알람에 대한 이동 상태를 변경할 권한이 없습니다.");
        }
    }

    private void givenCurrentMember() {
        Room room = roomWithId(ROOM_ID);
        given(roomRepository.findActiveById(ROOM_ID)).willReturn(Optional.of(room));
        given(roomUserRepository.findActiveByRoomIdAndUserId(ROOM_ID, USER_ID))
            .willReturn(Optional.of(RoomUser.of(userWithId(USER_ID), room)));
    }

    private Room roomWithId(Long roomId) {
        Room room = Room.of(userWithId(99L), null, "아침 모임", null,
            List.of(ActivityDay.TUESDAY), LocalTime.of(8, 0));
        ReflectionTestUtils.setField(room, "id", roomId);
        return room;
    }

    private User userWithId(Long userId) {
        User user = User.register(SocialProvider.KAKAO, "user-" + userId, null, NOW.minusDays(1));
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private User userWithId(Long userId, String nickname) {
        User user = userWithId(userId);
        user.changeNickname(nickname);
        return user;
    }

    private AlarmOccurrence alarmOccurrenceWith(User user, Long alarmOccurrenceId) {
        return alarmOccurrenceWith(user, alarmOccurrenceId, NOW.minusHours(1));
    }

    private AlarmOccurrence alarmOccurrenceWith(User user, Long alarmOccurrenceId, LocalDateTime startedAt) {
        AlarmOccurrence alarmOccurrence = AlarmOccurrence.start(user, "alarm-" + alarmOccurrenceId, startedAt,
            LocalTime.of(8, 0), startedAt);
        ReflectionTestUtils.setField(alarmOccurrence, "id", alarmOccurrenceId);
        return alarmOccurrence;
    }

    private void assertError(Throwable thrown, int status, String code, String message) {
        assertThat(thrown).isInstanceOfSatisfying(GeneralException.class, exception -> {
            assertThat(exception.getErrorReasonHttpStatus().httpStatus().value()).isEqualTo(status);
            assertThat(exception.getErrorReasonHttpStatus().code()).isEqualTo(code);
            assertThat(exception.getErrorReasonHttpStatus().message()).isEqualTo(message);
        });
    }
}
