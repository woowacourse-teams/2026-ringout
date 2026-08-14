package com.ringout.api.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Nickname {

  public static final int MAX_LENGTH = 10;

  @Column(name = "nickname", length = MAX_LENGTH)
  private String value;

  public Nickname(String value) {
    validate(value);
    this.value = value;
  }

  private void validate(String value) {
    validateRequired(value);
    validateMaxLength(value);
  }

  private void validateRequired(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("닉네임은 비어 있을 수 없습니다.");
    }
  }

  private void validateMaxLength(String value) {
    if (value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("닉네임은 최대 %d자입니다.".formatted(MAX_LENGTH));
    }
  }
}
