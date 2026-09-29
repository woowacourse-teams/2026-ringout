package com.ringout.api.alarmmovement.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;

import com.ringout.api.alarm.domain.ActiveAlarm;
import com.ringout.api.alarmmovement.status.AlarmMovementErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlarmMovementTest {

    private static final LocalDateTime ACTION_AT = LocalDateTime.of(2026, 9, 29, 8, 1);

    @Nested
    class 알람_이동_상태_반환 {

        @Test
        void 세_행동_시각이_모두_null이면_알람_트리거_상태를_반환한다() {
            // given
            AlarmMovement alarmMovement = alarmMovementWith(null, null, null);

            // when
            MovementStatus status = alarmMovement.getMovementStatus(ACTION_AT);

            // then
            assertThat(status).isEqualTo(MovementStatus.ALARM_TRIGGERED);
        }

        @Test
        void 이동_시작_시각만_존재하면_시작_상태를_반환한다() {
            // given
            AlarmMovement alarmMovement = alarmMovementWith(ACTION_AT, null, null);

            // when
            MovementStatus status = alarmMovement.getMovementStatus(ACTION_AT.plusMinutes(1));

            // then
            assertThat(status).isEqualTo(MovementStatus.STARTED);
        }

        @Test
        void 이동_시작_후_2분이_지나면_이동중_상태를_반환한다() {
            // given
            AlarmMovement alarmMovement = alarmMovementWith(ACTION_AT, null, null);

            // when
            MovementStatus status = alarmMovement.getMovementStatus(ACTION_AT.plusMinutes(2));

            // then
            assertThat(status).isEqualTo(MovementStatus.MOVING);
        }

        @Test
        void 이동_시작_기록_없이도_포기_시각이_존재하면_포기_상태를_반환한다() {
            // given
            AlarmMovement alarmMovement = alarmMovementWith(null, ACTION_AT, null);

            // when
            MovementStatus status = alarmMovement.getMovementStatus(ACTION_AT);

            // then
            assertThat(status).isEqualTo(MovementStatus.GAVE_UP);
        }

        @Test
        void 이동_시작_기록_없이도_도착_시각이_존재하면_도착_상태를_반환한다() {
            // given
            AlarmMovement alarmMovement = alarmMovementWith(null, null, ACTION_AT);

            // when
            MovementStatus status = alarmMovement.getMovementStatus(ACTION_AT);

            // then
            assertThat(status).isEqualTo(MovementStatus.ARRIVED);
        }
    }

    @Nested
    class 종료_시각_상호_배타_규칙 {

        @Test
        void 포기_시각과_도착_시각이_함께_존재하면_생성할_수_없다() {
            // given
            ActiveAlarm activeAlarm = mock(ActiveAlarm.class);

            // when
            Throwable thrown = catchThrowable(() -> AlarmMovement.of(activeAlarm, null, ACTION_AT, ACTION_AT));

            // then
            assertThat(thrown)
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode())
                        .isEqualTo(AlarmMovementErrorStatus.MOVEMENT_TERMINAL_STATE_CONFLICT));
        }
    }

    private AlarmMovement alarmMovementWith(
        LocalDateTime startedAt, LocalDateTime gaveUpAt, LocalDateTime arrivedAt
    ) {
        return AlarmMovement.of(mock(ActiveAlarm.class), startedAt, gaveUpAt, arrivedAt);
    }
}
