package com.ringout.api.alarm.domain;

import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Latitude {

    private static final double MIN_LATITUDE = -90.0;
    private static final double MAX_LATITUDE = 90.0;

    @Column(name = "destination_latitude", nullable = false)
    private double value;

    private Latitude(double value) {
        this.value = value;
    }

    public static Latitude from(Double value) {
        if (value == null || !isInRange(value)) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
        return new Latitude(value);
    }

    private static boolean isInRange(double value) {
        return value >= MIN_LATITUDE && value <= MAX_LATITUDE;
    }
}
