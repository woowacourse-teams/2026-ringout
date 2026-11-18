package com.ringout.api.alarm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.time.LocalTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmSchedule {

    @Column(name = "alarm_days", nullable = false)
    @Convert(converter = AlarmDaysConverter.class)
    private AlarmDays days;

    @Embedded
    private RingingRule ringingRule;

    private AlarmSchedule(AlarmDays days, RingingRule ringingRule) {
        this.days = days;
        this.ringingRule = ringingRule;
    }

    public static AlarmSchedule of(List<AlarmDay> days, RingingRule ringingRule) {
        return new AlarmSchedule(AlarmDays.from(days), ringingRule);
    }

    public List<AlarmDay> days() {
        return days.days();
    }

    public int daysMask() {
        return days.toMask();
    }

    public LocalTime alarmTime() {
        return ringingRule.alarmTime();
    }

    public int repeatMinutes() {
        return ringingRule.repeatMinutes();
    }
}
