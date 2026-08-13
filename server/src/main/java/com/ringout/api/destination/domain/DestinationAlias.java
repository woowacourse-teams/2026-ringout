package com.ringout.api.destination.domain;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.status.DestinationErrorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DestinationAlias {

    private static final int MAX_LENGTH = 12;

    @Column(name = "alias", nullable = false, length = MAX_LENGTH)
    private String value;

    private DestinationAlias(String value) {
        String normalizedValue = normalize(value);
        validate(normalizedValue);
        this.value = normalizedValue;
    }

    public static DestinationAlias from(String value) {
        return new DestinationAlias(value);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private void validate(String value) {
        validateRequired(value);
        validateMaxLength(value);
    }

    private void validateRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ALIAS_INVALID);
        }
    }

    private void validateMaxLength(String value) {
        if (value.length() > MAX_LENGTH) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ALIAS_INVALID);
        }
    }
}
