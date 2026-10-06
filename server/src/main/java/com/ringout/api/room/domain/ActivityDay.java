package com.ringout.api.room.domain;

import java.util.Arrays;
import java.util.List;

public enum ActivityDay {
    MONDAY(64),
    TUESDAY(32),
    WEDNESDAY(16),
    THURSDAY(8),
    FRIDAY(4),
    SATURDAY(2),
    SUNDAY(1);

    private static final int MIN_ACTIVITY_DAYS_MASK = 0;
    private static final int MAX_ACTIVITY_DAYS_MASK = 127;

    private final int mask;

    ActivityDay(int mask) {
        this.mask = mask;
    }

    public static List<ActivityDay> from(int activityDays) {
        validate(activityDays);

        return Arrays.stream(values())
            .filter(activityDay -> (activityDays & activityDay.mask) != 0)
            .toList();
    }

    public int getMask() {
        return mask;
    }

    static int toMask(List<ActivityDay> activityDays) {
        int mask = 0;
        for (ActivityDay activityDay : activityDays) {
            mask |= activityDay.mask;
        }
        return mask;
    }

    private static void validate(int activityDays) {
        if (activityDays < MIN_ACTIVITY_DAYS_MASK
            || activityDays > MAX_ACTIVITY_DAYS_MASK) {
            throw new IllegalArgumentException("올바르지 요일입니다.");
        }
    }

}
