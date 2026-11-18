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
public class AlarmVersion {

    @Column(name = "alarm_version", nullable = false)
    private Long value;

    private AlarmVersion(Long value) {
        this.value = value;
    }

    public static AlarmVersion from(Long value) {
        if (value == null) {
            throw new GeneralException(AlarmErrorStatus.ALARM_VERSION_INVALID);
        }
        return new AlarmVersion(value);
    }
}
