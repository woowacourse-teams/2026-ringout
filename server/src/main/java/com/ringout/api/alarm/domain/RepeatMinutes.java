package com.ringout.api.alarm.domain;

import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RepeatMinutes {

    private static final int MIN_MINUTES = 0;
    private static final int MAX_MINUTES = 30;

    @Column(name = "repeat_minutes", nullable = false)
    private int value;

    private RepeatMinutes(int value) {
        this.value = value;
    }

    public static RepeatMinutes from(Integer value) {
        if (value == null || value < MIN_MINUTES || value > MAX_MINUTES) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
        return new RepeatMinutes(value);
    }
}
