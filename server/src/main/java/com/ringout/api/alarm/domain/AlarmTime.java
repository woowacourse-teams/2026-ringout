package com.ringout.api.alarm.domain;

import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.time.LocalTime;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlarmTime {

    @Column(name = "alarm_time", nullable = false)
    private LocalTime value;

    private AlarmTime(LocalTime value) {
        this.value = value;
    }

    public static AlarmTime from(LocalTime value) {
        if (value == null) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
        return new AlarmTime(value);
    }
}
