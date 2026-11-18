package com.ringout.api.config.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class YearMonthConverterTest {

    private final YearMonthConverter converter = new YearMonthConverter();

    @Nested
    class 연월_문자열_변환 {

        @Test
        void 네_자리_연도와_두_자리_월을_연월로_변환한다() {
            // given
            String source = "2026-08";

            // when
            YearMonth yearMonth = converter.convert(source);

            // then
            assertThat(yearMonth).isEqualTo(YearMonth.of(2026, 8));
        }

        @Test
        void 십이월을_변환한다() {
            // given
            String source = "2026-12";

            // when
            YearMonth yearMonth = converter.convert(source);

            // then
            assertThat(yearMonth).isEqualTo(YearMonth.of(2026, 12));
        }

        @ParameterizedTest
        @ValueSource(strings = {"2026-8", "2026-13", "2026-00", "2026-08-17", "2026/08", "202608", "abcd-ef",
            "+2026-08", "+20260-08", "20260-08", "-0001-01", "+999999999-12", " 2026-08", "2026-08 ", ""})
        void 네_자리_연도와_두_자리_월_형식이_아니면_변환할_수_없다(String source) {
            // given

            // when // then
            assertThatThrownBy(() -> converter.convert(source))
                .isInstanceOf(DateTimeParseException.class);
        }
    }
}
