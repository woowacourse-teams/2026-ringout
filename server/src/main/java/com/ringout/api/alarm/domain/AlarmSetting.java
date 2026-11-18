package com.ringout.api.alarm.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmSetting {

    @Embedded
    private AlarmSchedule schedule;

    @Embedded
    private AlarmDestination destination;

    private AlarmSetting(AlarmSchedule schedule, AlarmDestination destination) {
        this.schedule = schedule;
        this.destination = destination;
    }

    public static AlarmSetting of(LocalTime alarmTime, List<AlarmDay> days, Integer repeatMinutes,
        String destinationName, Double latitude, Double longitude) {
        AlarmSchedule schedule = AlarmSchedule.of(days, RingingRule.of(alarmTime, repeatMinutes));
        AlarmDestination destination = AlarmDestination.of(destinationName, AlarmCoordinate.of(latitude, longitude));
        return new AlarmSetting(schedule, destination);
    }

    public LocalTime alarmTime() {
        return schedule.alarmTime();
    }

    public List<AlarmDay> days() {
        return schedule.days();
    }

    public int daysMask() {
        return schedule.daysMask();
    }

    public int repeatMinutes() {
        return schedule.repeatMinutes();
    }

    public String destinationName() {
        return destination.name();
    }

    public double latitude() {
        return destination.latitude();
    }

    public double longitude() {
        return destination.longitude();
    }
}
