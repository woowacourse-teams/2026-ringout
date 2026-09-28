package com.ringout.api.room.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ActivityDaysConverterTest {

    private final ActivityDaysConverter converter = new ActivityDaysConverter();

    @Nested
    class 활동_요일_데이터베이스_변환_규칙 {

        @Test
        void 활동_요일_객체를_DB_정수로_변환한다() {
            // given
            ActivityDays days = ActivityDays.from(List.of(
                ActivityDay.MONDAY,
                ActivityDay.WEDNESDAY,
                ActivityDay.FRIDAY
            ));

            // when
            Integer databaseValue = converter.convertToDatabaseColumn(days);

            // then
            assertThat(databaseValue).isEqualTo(84);
        }

        @Test
        void DB_정수를_활동_요일_객체로_변환한다() {
            // given
            int databaseValue = 84;

            // when
            ActivityDays days = converter.convertToEntityAttribute(databaseValue);

            // then
            assertThat(days.getDays())
                .containsExactly(ActivityDay.MONDAY, ActivityDay.WEDNESDAY, ActivityDay.FRIDAY);
        }

        @Test
        void null_활동_요일_객체를_null_DB_정수로_변환한다() {
            // given
            ActivityDays days = null;

            // when
            Integer convertedDatabaseValue = converter.convertToDatabaseColumn(days);

            // then
            assertThat(convertedDatabaseValue).isNull();
        }

        @Test
        void null_DB_정수를_null_활동_요일_객체로_변환한다() {
            // given
            Integer databaseValue = null;

            // when
            ActivityDays convertedDays = converter.convertToEntityAttribute(databaseValue);

            // then
            assertThat(convertedDays).isNull();
        }
    }
}
