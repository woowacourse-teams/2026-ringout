package com.ringout.api.room.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class ActivityDaysConverter implements AttributeConverter<ActivityDays, Integer> {

    @Override
    public Integer convertToDatabaseColumn(ActivityDays activityDays) {
        if (activityDays == null) {
            return null;
        }
        return activityDays.toMask();
    }

    @Override
    public ActivityDays convertToEntityAttribute(Integer activityDays) {
        if (activityDays == null) {
            return null;
        }
        return ActivityDays.fromMask(activityDays);
    }
}
