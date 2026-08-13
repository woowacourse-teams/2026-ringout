package com.ringout.api.destination.domain;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.status.DestinationErrorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coordinate {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;
    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    private Coordinate(Double latitude, Double longitude) {
        validate(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static Coordinate of(Double latitude, Double longitude) {
        return new Coordinate(latitude, longitude);
    }

    private void validate(Double latitude, Double longitude) {
        validateRequired(latitude, longitude);
        validateFinite(latitude, longitude);
        validateLatitudeRange(latitude);
        validateLongitudeRange(longitude);
    }

    private void validateRequired(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID);
        }
    }

    private void validateFinite(Double latitude, Double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID);
        }
    }

    private void validateLatitudeRange(Double latitude) {
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID);
        }
    }

    private void validateLongitudeRange(Double longitude) {
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_COORDINATE_INVALID);
        }
    }
}
