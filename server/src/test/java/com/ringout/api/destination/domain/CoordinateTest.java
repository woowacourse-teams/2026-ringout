package com.ringout.api.destination.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.status.DestinationErrorStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CoordinateTest {

    @Nested
    class 좌표_생성 {

        @Test
        void 경계값의_좌표를_생성할_수_있다() {
            // given
            double minLatitude = -90.0;
            double minLongitude = -180.0;
            double maxLatitude = 90.0;
            double maxLongitude = 180.0;

            // when
            Coordinate minCoordinate = Coordinate.of(minLatitude, minLongitude);
            Coordinate maxCoordinate = Coordinate.of(maxLatitude, maxLongitude);

            // then
            assertThat(minCoordinate.getLatitude()).isEqualTo(minLatitude);
            assertThat(minCoordinate.getLongitude()).isEqualTo(minLongitude);
            assertThat(maxCoordinate.getLatitude()).isEqualTo(maxLatitude);
            assertThat(maxCoordinate.getLongitude()).isEqualTo(maxLongitude);
        }

        @Test
        void 범위를_벗어난_위도는_생성할_수_없다() {
            // given
            double invalidLatitude = 90.1;
            double longitude = 127.0276;

            // when // then
            assertThatThrownBy(() -> Coordinate.of(invalidLatitude, longitude))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID));
        }

        @Test
        void 범위를_벗어난_경도는_생성할_수_없다() {
            // given
            double latitude = 37.4979;
            double invalidLongitude = 180.1;

            // when // then
            assertThatThrownBy(() -> Coordinate.of(latitude, invalidLongitude))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID));
        }

        @Test
        void 숫자가_아닌_값과_무한대_좌표는_생성할_수_없다() {
            // given
            double latitude = 37.4979;
            double longitude = 127.0276;

            // when // then
            assertThatThrownBy(() -> Coordinate.of(Double.NaN, longitude))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID));

            assertThatThrownBy(() -> Coordinate.of(latitude, Double.POSITIVE_INFINITY))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID));
        }
    }
}
