package com.ringout.api.destination.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class DestinationTest {

    @Nested
    class 목적지_id_규칙 {

        @Test
        void 목적지_id는_36자까지_허용한다() {
            // given
            String destinationId = "123456789012345678901234567890123456";

            // when
            Destination destination = Destination.create(
                mock(User.class), destinationId, DestinationAlias.from("집"), Coordinate.of(37.5, 127.0)
            );

            // then
            assertThat(destination.getDestinationId()).isEqualTo(destinationId);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", " dest", "dest ", "dest 1", "1234567890123456789012345678901234567"})
        void 목적지_id가_비어있거나_공백을_포함하거나_36자를_넘으면_생성할_수_없다(String destinationId) {
            // given
            User user = mock(User.class);

            // when // then
            assertThatThrownBy(() -> Destination.create(
                user, destinationId, DestinationAlias.from("집"), Coordinate.of(37.5, 127.0)
            ))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_ID_INVALID));
        }
    }
}
