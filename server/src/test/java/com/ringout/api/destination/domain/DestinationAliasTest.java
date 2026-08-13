package com.ringout.api.destination.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.status.DestinationErrorStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DestinationAliasTest {

    @Nested
    class 별칭_생성 {

        @Test
        void 별칭은_저장하기_전에_앞뒤_공백을_제거한다() {
            // given
            String value = "  헬스장  ";

            // when
            DestinationAlias alias = DestinationAlias.from(value);

            // then
            assertThat(alias.getValue()).isEqualTo("헬스장");
        }

        @Test
        void 빈_별칭은_생성할_수_없다() {
            // given
            String value = " ";

            // when // then
            assertThatThrownBy(() -> DestinationAlias.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_ALIAS_INVALID));
        }

        @Test
        void 열두자를_초과하는_별칭은_생성할_수_없다() {
            // given
            String value = "1234567890123";

            // when // then
            assertThatThrownBy(() -> DestinationAlias.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_ALIAS_INVALID));
        }
    }
}
