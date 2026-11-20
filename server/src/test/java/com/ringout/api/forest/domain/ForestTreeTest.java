package com.ringout.api.forest.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ForestTreeTest {

    private static final LocalDateTime PLANTED_AT = LocalDateTime.of(2026, 11, 5, 7, 48, 10);

    @Nested
    class 나무_회차_규칙 {

        @ParameterizedTest
        @CsvSource({"1, 1", "16, 16", "007, 7"})
        void 회차는_1부터_16까지의_정수다(String rawGrowthCycle, int expected) {
            // when
            int growthCycle = ForestTree.parseGrowthCycle(rawGrowthCycle);

            // then
            assertThat(growthCycle).isEqualTo(expected);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", "0", "17", "-1", "1.5", "abc", "99999999999"})
        void 회차가_1부터_16까지의_정수가_아니면_FOREST400을_던진다(String rawGrowthCycle) {
            // when // then
            assertThatThrownBy(() -> ForestTree.parseGrowthCycle(rawGrowthCycle))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_GROWTH_CYCLE_INVALID));
        }
    }

    @Nested
    class 나무_심기 {

        @Test
        void 심은_나무는_확인_시각_없이_초_단위_획득_시각으로_저장한다() {
            // when
            ForestTree tree = ForestTree.plant(mock(User.class), 1, TreeKind.PINE, 7, PLANTED_AT.plusNanos(734_000_000));

            // then
            assertThat(tree.getGrowthCycle()).isEqualTo(1);
            assertThat(tree.getKind()).isEqualTo(TreeKind.PINE);
            assertThat(tree.getSlotIndex()).isEqualTo(7);
            assertThat(tree.getPlantedAt()).isEqualTo(PLANTED_AT);
            assertThat(tree.getAcknowledgedAt()).isNull();
        }

        @Test
        void 나무_종류가_없으면_FOREST400을_던진다() {
            // given
            User user = mock(User.class);

            // when // then
            assertThatThrownBy(() -> ForestTree.plant(user, 1, null, 7, PLANTED_AT))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID));
        }

        @Test
        void 획득_시각이_없으면_FOREST400을_던진다() {
            // given
            User user = mock(User.class);

            // when // then
            assertThatThrownBy(() -> ForestTree.plant(user, 1, TreeKind.PINE, 7, null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(ForestErrorStatus.FOREST_TREE_REQUEST_INVALID));
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 16})
        void 칸_번호가_0부터_15가_아니면_심을_수_없다(int slotIndex) {
            // given
            User user = mock(User.class);

            // when // then
            assertThatThrownBy(() -> ForestTree.plant(user, 1, TreeKind.PINE, slotIndex, PLANTED_AT))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class 나무_확인 {

        private static final LocalDateTime ACKNOWLEDGED_AT = LocalDateTime.of(2026, 11, 10, 8, 1, 0);

        @Test
        void 확인_시각이_없으면_초_단위로_저장하고_확인했다고_알린다() {
            // given
            ForestTree tree = ForestTree.plant(mock(User.class), 1, TreeKind.PINE, 7, PLANTED_AT);

            // when
            boolean acknowledged = tree.acknowledge(ACKNOWLEDGED_AT.plusNanos(734_000_000));

            // then
            assertThat(acknowledged).isTrue();
            assertThat(tree.getAcknowledgedAt()).isEqualTo(ACKNOWLEDGED_AT);
        }

        @Test
        void 이미_확인한_나무는_처음_확인한_시각을_유지한다() {
            // given
            ForestTree tree = ForestTree.plant(mock(User.class), 1, TreeKind.PINE, 7, PLANTED_AT);
            tree.acknowledge(ACKNOWLEDGED_AT);

            // when
            boolean acknowledged = tree.acknowledge(ACKNOWLEDGED_AT.plusDays(1));

            // then
            assertThat(acknowledged).isFalse();
            assertThat(tree.getAcknowledgedAt()).isEqualTo(ACKNOWLEDGED_AT);
        }

        @Test
        void 확인해도_나무의_다른_정보는_바뀌지_않는다() {
            // given
            ForestTree tree = ForestTree.plant(mock(User.class), 1, TreeKind.PINE, 7, PLANTED_AT);

            // when
            tree.acknowledge(ACKNOWLEDGED_AT);

            // then
            assertThat(tree.getGrowthCycle()).isEqualTo(1);
            assertThat(tree.getKind()).isEqualTo(TreeKind.PINE);
            assertThat(tree.getSlotIndex()).isEqualTo(7);
            assertThat(tree.getPlantedAt()).isEqualTo(PLANTED_AT);
        }

        @Test
        void 확인_시각이_없으면_FOREST400을_던진다() {
            // given
            ForestTree tree = ForestTree.plant(mock(User.class), 1, TreeKind.PINE, 7, PLANTED_AT);

            // when // then
            assertThatThrownBy(() -> tree.acknowledge(null))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode())
                        .isEqualTo(ForestErrorStatus.FOREST_TREE_ACKNOWLEDGE_REQUEST_INVALID));
            assertThat(tree.getAcknowledgedAt()).isNull();
        }
    }
}
