package com.ringout.api.alarm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AlarmDaysTest {

    @Nested
    class 알람_요일_생성 {

        @Test
        void 요일_목록을_비트마스크로_변환한다() {
            // given
            List<AlarmDay> days = List.of(AlarmDay.MONDAY, AlarmDay.FRIDAY);

            // when
            AlarmDays alarmDays = AlarmDays.from(days);

            // then
            assertThat(alarmDays.toMask()).isEqualTo(64 + 4);
            assertThat(alarmDays.days()).containsExactly(AlarmDay.MONDAY, AlarmDay.FRIDAY);
        }

        @Test
        void 빈_요일_목록은_반복_없는_알람으로_비트마스크_0이다() {
            // given
            List<AlarmDay> days = List.of();

            // when
            AlarmDays alarmDays = AlarmDays.from(days);

            // then
            assertThat(alarmDays.toMask()).isZero();
            assertThat(alarmDays.days()).isEmpty();
        }

        @Test
        void 요일은_입력_순서와_관계없이_월요일부터_일요일_순으로_반환한다() {
            // given
            List<AlarmDay> days = List.of(AlarmDay.SUNDAY, AlarmDay.WEDNESDAY, AlarmDay.MONDAY);

            // when
            AlarmDays alarmDays = AlarmDays.from(days);

            // then
            assertThat(alarmDays.days()).containsExactly(AlarmDay.MONDAY, AlarmDay.WEDNESDAY, AlarmDay.SUNDAY);
        }

        @Test
        void 중복된_요일이_있으면_생성할_수_없다() {
            // given
            List<AlarmDay> days = List.of(AlarmDay.MONDAY, AlarmDay.MONDAY);

            // when // then
            assertThatThrownBy(() -> AlarmDays.from(days))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }

        @Test
        void 요일_목록이_없으면_생성할_수_없다() {
            // given
            List<AlarmDay> days = null;

            // when // then
            assertThatThrownBy(() -> AlarmDays.from(days))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }

        @Test
        void 요일_목록에_null이_있으면_생성할_수_없다() {
            // given
            List<AlarmDay> days = Arrays.asList(AlarmDay.MONDAY, null);

            // when // then
            assertThatThrownBy(() -> AlarmDays.from(days))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }
    }

    @Nested
    class 알람_요일_비트마스크_복원 {

        @Test
        void 모든_요일의_비트마스크를_요일_목록으로_복원한다() {
            // given
            int mask = 127;

            // when
            AlarmDays alarmDays = AlarmDays.fromMask(mask);

            // then
            assertThat(alarmDays.days()).containsExactly(AlarmDay.values());
        }

        @Test
        void 비트마스크로_만든_요일은_같은_비트마스크로_다시_변환된다() {
            // given
            int mask = 64 + 16 + 1;

            // when
            AlarmDays alarmDays = AlarmDays.fromMask(mask);

            // then
            assertThat(alarmDays.toMask()).isEqualTo(mask);
            assertThat(alarmDays.days()).containsExactly(AlarmDay.MONDAY, AlarmDay.WEDNESDAY, AlarmDay.SUNDAY);
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 128})
        void 범위를_벗어난_비트마스크는_복원할_수_없다(int mask) {
            // given

            // when // then
            assertThatThrownBy(() -> AlarmDays.fromMask(mask))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
