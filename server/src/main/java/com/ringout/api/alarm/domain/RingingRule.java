package com.ringout.api.alarm.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RingingRule {

    @Embedded
    private AlarmTime alarmTime;

    @Embedded
    private RepeatMinutes repeatMinutes;

    private RingingRule(AlarmTime alarmTime, RepeatMinutes repeatMinutes) {
        this.alarmTime = alarmTime;
        this.repeatMinutes = repeatMinutes;
    }

    public static RingingRule of(LocalTime alarmTime, Integer repeatMinutes) {
        return new RingingRule(AlarmTime.from(alarmTime), RepeatMinutes.from(repeatMinutes));
    }

    public LocalTime alarmTime() {
        return alarmTime.getValue();
    }

    public int repeatMinutes() {
        return repeatMinutes.getValue();
    }
}
