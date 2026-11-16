package com.ringout.api.forest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ForestGrowthTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0, 5, false",
        "1, 1, 4, false",
        "4, 4, 1, false",
        "5, 0, 5, false",
        "6, 1, 4, false",
        "23, 3, 2, false",
        "79, 4, 1, false",
        "80, 0, 0, true",
        "95, 0, 0, true"
    })
    void 성공_날짜_수로_씨앗_성장_상태를_계산한다(int successfulDayCount, int filledSlots, int remainingDays,
        boolean complete) {
        // when
        ForestGrowth growth = ForestGrowth.of(successfulDayCount);

        // then
        assertThat(growth.getSuccessfulDayCount()).isEqualTo(successfulDayCount);
        assertThat(growth.getFilledSlots()).isEqualTo(filledSlots);
        assertThat(growth.getRemainingDays()).isEqualTo(remainingDays);
        assertThat(growth.isComplete()).isEqualTo(complete);
    }

    @Test
    void 성공_날짜_수는_음수일_수_없다() {
        // when // then
        assertThatThrownBy(() -> ForestGrowth.of(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
