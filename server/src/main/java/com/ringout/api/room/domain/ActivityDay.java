package com.ringout.api.room.domain;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;

public enum ActivityDay {
    MONDAY(64, DayOfWeek.MONDAY),
    TUESDAY(32, DayOfWeek.TUESDAY),
    WEDNESDAY(16, DayOfWeek.WEDNESDAY),
    THURSDAY(8, DayOfWeek.THURSDAY),
    FRIDAY(4, DayOfWeek.FRIDAY),
    SATURDAY(2, DayOfWeek.SATURDAY),
    SUNDAY(1, DayOfWeek.SUNDAY);

    private static final int MIN_ACTIVITY_DAYS_MASK = 0;
    private static final int MAX_ACTIVITY_DAYS_MASK = 127;

    private final int mask;
    private final DayOfWeek dayOfWeek;

    ActivityDay(int mask, DayOfWeek dayOfWeek) {
        this.mask = mask;
        this.dayOfWeek = dayOfWeek;
    }

    public static ActivityDay from(DayOfWeek dayOfWeek) {
        return Arrays.stream(values())
            .filter(activityDay -> activityDay.dayOfWeek == dayOfWeek)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("올바르지 않은 요일입니다."));
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
