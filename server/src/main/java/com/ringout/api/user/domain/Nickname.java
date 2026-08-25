package com.ringout.api.user.domain;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.status.UserErrorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nickname {

  public static final int MAX_LENGTH = 10;
  private static final Pattern ALLOWED_PATTERN = Pattern.compile("^[가-힣A-Za-z0-9]+$");

  @Column(name = "nickname", nullable = false, length = MAX_LENGTH)
  private String value;

  public Nickname(String value) {
    validate(value);
    this.value = value;
  }

  private void validate(String value) {
    validateRequired(value);
    validateMaxLength(value);
    validateFormat(value);
  }

  private void validateRequired(String value) {
    if (value == null || value.isBlank()) {
      throw new GeneralException(UserErrorStatus.USER_NICKNAME_REQUIRED);
    }
  }

  private void validateMaxLength(String value) {
    if (value.length() > MAX_LENGTH) {
      throw new GeneralException(UserErrorStatus.USER_NICKNAME_TOO_LONG);
    }
  }

  private void validateFormat(String value) {
    if (!ALLOWED_PATTERN.matcher(value).matches()) {
      throw new GeneralException(UserErrorStatus.USER_NICKNAME_INVALID_FORMAT);
    }
  }
}
