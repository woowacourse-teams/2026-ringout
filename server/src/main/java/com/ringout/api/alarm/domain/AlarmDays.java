package com.ringout.api.alarm.domain;

import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public final class AlarmDays {

    private static final int MIN_MASK = 0;
    private static final int MAX_MASK = 127;

    private final List<AlarmDay> days;

    private AlarmDays(List<AlarmDay> days) {
        this.days = days.stream()
            .sorted()
            .toList();
    }

    public static AlarmDays from(List<AlarmDay> days) {
        validateDays(days);
        return new AlarmDays(days);
    }

    public static AlarmDays fromMask(int mask) {
        validateMask(mask);
        List<AlarmDay> days = Arrays.stream(AlarmDay.values())
            .filter(day -> day.isIncludedIn(mask))
            .toList();
        return new AlarmDays(days);
    }

    public int toMask() {
        return days.stream()
            .mapToInt(AlarmDay::mask)
            .sum();
    }

    public List<AlarmDay> days() {
        return days;
    }

    private static void validateDays(List<AlarmDay> days) {
        if (days == null || days.stream().anyMatch(Objects::isNull)) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
        if (new HashSet<>(days).size() != days.size()) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
    }

    private static void validateMask(int mask) {
        if (mask < MIN_MASK || mask > MAX_MASK) {
            throw new IllegalArgumentException("올바르지 않은 알람 요일입니다.");
        }
    }
}
