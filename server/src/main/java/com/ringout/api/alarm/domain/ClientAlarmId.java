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
public class ClientAlarmId {

    private static final int MAX_LENGTH = 36;

    @Column(name = "alarm_id", nullable = false, length = MAX_LENGTH)
    private String value;

    private ClientAlarmId(String value) {
        this.value = value;
    }

    public static ClientAlarmId from(String value) {
        if (!isValid(value)) {
            throw new GeneralException(AlarmErrorStatus.ALARM_ID_INVALID);
        }
        return new ClientAlarmId(value);
    }

    private static boolean isValid(String value) {
        return value != null && !value.isBlank() && !value.contains(" ") && value.length() <= MAX_LENGTH;
    }
}
