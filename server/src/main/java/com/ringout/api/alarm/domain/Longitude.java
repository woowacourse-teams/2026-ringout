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
public class Longitude {

    private static final double MIN_LONGITUDE = -180.0;
    private static final double MAX_LONGITUDE = 180.0;

    @Column(name = "destination_longitude", nullable = false)
    private double value;

    private Longitude(double value) {
        this.value = value;
    }

    public static Longitude from(Double value) {
        if (value == null || !isInRange(value)) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
        return new Longitude(value);
    }

    private static boolean isInRange(double value) {
        return value >= MIN_LONGITUDE && value <= MAX_LONGITUDE;
    }
}
