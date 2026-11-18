package com.ringout.api.alarm.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmCoordinate {

    @Embedded
    private Latitude latitude;

    @Embedded
    private Longitude longitude;

    private AlarmCoordinate(Latitude latitude, Longitude longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public static AlarmCoordinate of(Double latitude, Double longitude) {
        return new AlarmCoordinate(Latitude.from(latitude), Longitude.from(longitude));
    }

    public double latitude() {
        return latitude.getValue();
    }

    public double longitude() {
        return longitude.getValue();
    }
}
