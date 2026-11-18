package com.ringout.api.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ActivityDayTest {

    @ParameterizedTest
    @EnumSource(DayOfWeek.class)
    void DayOfWeek를_같은_요일의_활동_요일로_변환한다(DayOfWeek dayOfWeek) {
        // when
        ActivityDay activityDay = ActivityDay.from(dayOfWeek);

        // then
        assertThat(activityDay.name()).isEqualTo(dayOfWeek.name());
    }
}
