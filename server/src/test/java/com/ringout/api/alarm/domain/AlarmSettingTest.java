package com.ringout.api.alarm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AlarmSettingTest {

    @Nested
    class 알람_설정_생성 {

        @Test
        void 알람_설정_전체를_값_객체로_구성한다() {
            // given
            List<AlarmDay> days = List.of(AlarmDay.FRIDAY, AlarmDay.MONDAY);

            // when
            AlarmSetting setting = AlarmSetting.of(LocalTime.of(7, 0), days, 5, "세종대학교", 37.5502, 127.0738);

            // then
            assertThat(setting.alarmTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(setting.days()).containsExactly(AlarmDay.MONDAY, AlarmDay.FRIDAY);
            assertThat(setting.daysMask()).isEqualTo(64 + 4);
            assertThat(setting.repeatMinutes()).isEqualTo(5);
            assertThat(setting.destinationName()).isEqualTo("세종대학교");
            assertThat(setting.latitude()).isEqualTo(37.5502);
            assertThat(setting.longitude()).isEqualTo(127.0738);
        }

        @Test
        void 설정_값_중_하나라도_올바르지_않으면_생성할_수_없다() {
            // given
            List<AlarmDay> days = List.of(AlarmDay.MONDAY);

            // when // then
            assertThatThrownBy(() -> AlarmSetting.of(LocalTime.of(7, 0), days, 31, "세종대학교", 37.5502, 127.0738))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }
    }
}
