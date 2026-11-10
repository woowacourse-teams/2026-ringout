package com.ringout.api.alarm.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class AlarmDaysConverter implements AttributeConverter<AlarmDays, Integer> {

    @Override
    public Integer convertToDatabaseColumn(AlarmDays alarmDays) {
        if (alarmDays == null) {
            return null;
        }
        return alarmDays.toMask();
    }

    @Override
    public AlarmDays convertToEntityAttribute(Integer mask) {
        if (mask == null) {
            return null;
        }
        return AlarmDays.fromMask(mask);
    }
}
