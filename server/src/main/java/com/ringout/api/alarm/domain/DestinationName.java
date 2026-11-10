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
public class DestinationName {

    private static final int MAX_LENGTH = 20;

    @Column(name = "destination_name", nullable = false, length = MAX_LENGTH)
    private String value;

    private DestinationName(String value) {
        this.value = value;
    }

    public static DestinationName from(String value) {
        if (value == null || value.isBlank()) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
        return fromTrimmed(value.trim());
    }

    private static DestinationName fromTrimmed(String value) {
        if (value.length() > MAX_LENGTH) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
        return new DestinationName(value);
    }
}
