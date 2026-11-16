package com.ringout.api.forest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ForestArrivalDaysTest {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 11, 1);

    @Test
    void 같은_날의_여러_도착은_하루로_계산한다() {
        // given
        List<LocalDateTime> arrivedAts = List.of(
            FIRST_DAY.atTime(7, 0),
            FIRST_DAY.atTime(18, 0),
            FIRST_DAY.plusDays(3).atTime(8, 0)
        );

        // when
        ForestArrivalDays arrivalDays = ForestArrivalDays.from(arrivedAts);

        // then
        assertThat(arrivalDays.count()).isEqualTo(2);
    }

    @Test
    void 성공_날짜가_회차의_필요_일수에_도달하고_획득_날짜가_일치하면_획득_자격이_있다() {
        // given
        ForestArrivalDays arrivalDays = ForestArrivalDays.from(arrivedAtsOnDays(7));

        // when // then
        assertThat(arrivalDays.canPlant(1, FIRST_DAY.plusDays(4))).isTrue();
    }

    @Test
    void 성공_날짜가_회차의_필요_일수보다_적으면_획득_자격이_없다() {
        // given
        ForestArrivalDays arrivalDays = ForestArrivalDays.from(arrivedAtsOnDays(7));

        // when // then
        assertThat(arrivalDays.canPlant(2, FIRST_DAY.plusDays(9))).isFalse();
    }

    @Test
    void 획득_날짜가_회차의_필요_번째_성공_날짜와_다르면_획득_자격이_없다() {
        // given
        ForestArrivalDays arrivalDays = ForestArrivalDays.from(arrivedAtsOnDays(10));

        // when // then
        assertThat(arrivalDays.canPlant(1, FIRST_DAY.plusDays(5))).isFalse();
    }

    @Test
    void 도착_시각의_순서와_관계없이_날짜_순서로_계산한다() {
        // given
        List<LocalDateTime> arrivedAts = new ArrayList<>(arrivedAtsOnDays(5));
        arrivedAts.add(0, arrivedAts.remove(arrivedAts.size() - 1));

        // when
        ForestArrivalDays arrivalDays = ForestArrivalDays.from(arrivedAts);

        // then
        assertThat(arrivalDays.canPlant(1, FIRST_DAY.plusDays(4))).isTrue();
    }

    @Test
    void 도착_기록이_없으면_획득_자격이_없다() {
        // when
        ForestArrivalDays arrivalDays = ForestArrivalDays.from(List.of());

        // then
        assertThat(arrivalDays.count()).isZero();
        assertThat(arrivalDays.canPlant(1, FIRST_DAY)).isFalse();
    }

    private List<LocalDateTime> arrivedAtsOnDays(int days) {
        List<LocalDateTime> arrivedAts = new ArrayList<>();
        for (int day = 0; day < days; day++) {
            arrivedAts.add(FIRST_DAY.plusDays(day).atTime(7, 30));
        }
        return arrivedAts;
    }
}
