package com.ringout.api.alarmmovement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ringout.api.alarm.domain.ActiveAlarm;
import com.ringout.api.alarm.repository.ActiveAlarmRepository;
import com.ringout.api.alarmmovement.domain.AlarmMovement;
import com.ringout.api.alarmmovement.domain.MovementAction;
import com.ringout.api.alarmmovement.domain.MovementStatus;
import com.ringout.api.alarmmovement.dto.request.AlarmMovementRequest;
import com.ringout.api.alarmmovement.dto.response.AlarmMovementResponse;
import com.ringout.api.alarmmovement.repository.AlarmMovementRepository;
import com.ringout.api.alarmmovement.status.AlarmMovementErrorStatus;
import com.ringout.api.auth.social.SocialProvider;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomUser;
import com.ringout.api.room.repository.RoomRepository;
import com.ringout.api.room.repository.RoomUserRepository;
import com.ringout.api.user.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private static final Long ACTIVE_ALARM_ID = 135L;
    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-09-29T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomUserRepository roomUserRepository;

    @Mock
    private ActiveAlarmRepository activeAlarmRepository;

    @Mock
    private AlarmMovementRepository alarmMovementRepository;

    private AlarmMovementService alarmMovementService;

    @BeforeEach
    void setUp() {
        alarmMovementService = new AlarmMovementService(
            roomRepository, roomUserRepository, activeAlarmRepository, alarmMovementRepository, CLOCK
        );
    }

    @Nested
    class 모임_회원_알람_이동_행동_처리 {

        @ParameterizedTest
        @CsvSource({"START_MOVEMENT, MOVEMENT_STARTED", "GIVE_UP, GAVE_UP", "ARRIVE, ARRIVED"})
        void 활성_알람에_연결된_이동_상태를_변경하고_반환한다(MovementAction action, MovementStatus expectedStatus) {
            // given
            givenCurrentMember();
            ActiveAlarm activeAlarm = mock(ActiveAlarm.class);
            AlarmMovement alarmMovement = AlarmMovement.of(activeAlarm, null, null, null);
            given(activeAlarmRepository.findActiveById(ACTIVE_ALARM_ID)).willReturn(Optional.of(activeAlarm));
            given(alarmMovementRepository.findByActiveAlarm(activeAlarm)).willReturn(Optional.of(alarmMovement));
            AlarmMovementRequest request = new AlarmMovementRequest(ACTIVE_ALARM_ID, action);

            // when
            AlarmMovementResponse response = alarmMovementService.changeMovement(USER_ID, ROOM_ID, request);

            // then
            assertThat(response.status()).isEqualTo(expectedStatus);
            assertThat(alarmMovement.getMovementStatus(NOW)).isEqualTo(expectedStatus);
            verify(activeAlarmRepository).findActiveById(ACTIVE_ALARM_ID);
            verify(alarmMovementRepository).findByActiveAlarm(activeAlarm);
        }
    }

    @Nested
    class 모임_회원_권한_검증 {

        @Test
        void 모임의_현재_회원이_아니면_알람_이동_상태를_변경할_수_없다() {
            // given
            given(roomRepository.findActiveById(ROOM_ID)).willReturn(Optional.of(roomWithId(ROOM_ID)));
            given(roomUserRepository.findActiveByRoomIdAndUserId(ROOM_ID, USER_ID)).willReturn(Optional.empty());
            AlarmMovementRequest request = new AlarmMovementRequest(ACTIVE_ALARM_ID, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertError(thrown, 403, "MOVEMENT403", "해당 모임의 회원이 아닙니다.");
            verifyNoInteractions(activeAlarmRepository, alarmMovementRepository);
        }
    }

    @Nested
    class 활성_알람_검증 {

        @Test
        void 존재하지_않는_알람이면_알람_이동_상태를_변경할_수_없다() {
            // given
            givenCurrentMember();
            given(activeAlarmRepository.findActiveById(ACTIVE_ALARM_ID)).willReturn(Optional.empty());
            AlarmMovementRequest request = new AlarmMovementRequest(ACTIVE_ALARM_ID, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertError(thrown, 404, "ALARM404", "존재하지 않는 알람 입니다.");
            verifyNoInteractions(alarmMovementRepository);
        }

        @Test
        void 활성_알람에_연결된_이동_상태가_없으면_내부_정합성_오류를_반환한다() {
            // given
            givenCurrentMember();
            ActiveAlarm activeAlarm = mock(ActiveAlarm.class);
            given(activeAlarmRepository.findActiveById(ACTIVE_ALARM_ID)).willReturn(Optional.of(activeAlarm));
            given(alarmMovementRepository.findByActiveAlarm(activeAlarm)).willReturn(Optional.empty());
            AlarmMovementRequest request = new AlarmMovementRequest(ACTIVE_ALARM_ID, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertError(thrown, 500, "MOVEMENT500", "이동 상태를 처리할 수 없습니다.");
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_RECORD_MISSING));
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
            verifyNoInteractions(roomRepository, roomUserRepository, activeAlarmRepository, alarmMovementRepository);
        }

        @Test
        void 활성_알람_ID가_없으면_활성_알람_ID_누락_오류를_반환한다() {
            // given
            AlarmMovementRequest request = new AlarmMovementRequest(null, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_ACTIVE_ALARM_ID_REQUIRED));
            verifyNoInteractions(roomRepository, roomUserRepository, activeAlarmRepository, alarmMovementRepository);
        }

        @Test
        void 활성_알람_ID가_양수가_아니면_활성_알람_ID_형식_오류를_반환한다() {
            // given
            AlarmMovementRequest request = new AlarmMovementRequest(0L, MovementAction.START_MOVEMENT);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_ACTIVE_ALARM_ID_INVALID));
            verifyNoInteractions(roomRepository, roomUserRepository, activeAlarmRepository, alarmMovementRepository);
        }

        @Test
        void 이동_행동이_없으면_이동_행동_누락_오류를_반환한다() {
            // given
            AlarmMovementRequest request = new AlarmMovementRequest(ACTIVE_ALARM_ID, null);

            // when
            Throwable thrown = catchThrowable(() -> alarmMovementService.changeMovement(USER_ID, ROOM_ID, request));

            // then
            assertThat(thrown).isInstanceOfSatisfying(GeneralException.class,
                exception -> assertThat(exception.getCode())
                    .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_ACTION_REQUIRED));
            verifyNoInteractions(roomRepository, roomUserRepository, activeAlarmRepository, alarmMovementRepository);
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

    private void assertError(Throwable thrown, int status, String code, String message) {
        assertThat(thrown).isInstanceOfSatisfying(GeneralException.class, exception -> {
            assertThat(exception.getErrorReasonHttpStatus().httpStatus().value()).isEqualTo(status);
            assertThat(exception.getErrorReasonHttpStatus().code()).isEqualTo(code);
            assertThat(exception.getErrorReasonHttpStatus().message()).isEqualTo(message);
        });
    }
}
