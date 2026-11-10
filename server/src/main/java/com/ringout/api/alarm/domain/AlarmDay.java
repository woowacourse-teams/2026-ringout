package com.ringout.api.alarm.domain;

public enum AlarmDay {
    MONDAY(64),
    TUESDAY(32),
    WEDNESDAY(16),
    THURSDAY(8),
    FRIDAY(4),
    SATURDAY(2),
    SUNDAY(1);

    private final int mask;

    AlarmDay(int mask) {
        this.mask = mask;
    }

    int mask() {
        return mask;
    }

    boolean isIncludedIn(int daysMask) {
        return (daysMask & mask) != 0;
    }
}
