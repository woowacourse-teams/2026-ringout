package com.ringout.api.alarm.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmDestination {

    @Embedded
    private DestinationName name;

    @Embedded
    private AlarmCoordinate coordinate;

    private AlarmDestination(DestinationName name, AlarmCoordinate coordinate) {
        this.name = name;
        this.coordinate = coordinate;
    }

    public static AlarmDestination of(String name, AlarmCoordinate coordinate) {
        return new AlarmDestination(DestinationName.from(name), coordinate);
    }

    public String name() {
        return name.getValue();
    }

    public double latitude() {
        return coordinate.latitude();
    }

    public double longitude() {
        return coordinate.longitude();
    }
}
