package com.ringout.api.room.domain;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.room.status.RoomErrorStatus;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;

public final class ActivityDays {

    @Getter
    private final List<ActivityDay> days;
    private final int mask;

    private ActivityDays(List<ActivityDay> days) {
        validateDays(days);
        this.mask = ActivityDay.toMask(days);
        this.days = List.copyOf(days);
    }

    public static ActivityDays from(List<ActivityDay> days) {
        return new ActivityDays(days);
    }

    public static ActivityDays fromMask(int mask) {
        return new ActivityDays(ActivityDay.from(mask));
    }

    int toMask() {
        return mask;
    }

    public boolean contains(ActivityDay activityDay) {
        return days.contains(activityDay);
    }

    public ActivityDays add(ActivityDay activityDay) {
        if (contains(activityDay)) {
            return this;
        }

        List<ActivityDay> updatedDays = new ArrayList<>(days);
        updatedDays.add(activityDay);
        return ActivityDays.from(updatedDays);
    }

    public ActivityDays remove(ActivityDay activityDay) {
        List<ActivityDay> updatedDays = new ArrayList<>(days);
        updatedDays.remove(activityDay);
        return ActivityDays.from(updatedDays);
    }

    private static void validateDays(List<ActivityDay> days) {
        if (days == null || days.isEmpty()) {
            throw new GeneralException(RoomErrorStatus.ROOM_ACTIVITY_DAYS_REQUIRED);
        }

        Set<ActivityDay> uniqueDays = new HashSet<>();
        for (ActivityDay day : days) {
            if (day == null || !uniqueDays.add(day)) {
                throw new GeneralException(RoomErrorStatus.ROOM_ACTIVITY_DAY_INVALID);
            }
        }
    }
}
