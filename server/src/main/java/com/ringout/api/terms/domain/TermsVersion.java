package com.ringout.api.terms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsVersion {

  @Column(name = "version", nullable = false)
  private LocalDate version;

  public TermsVersion(LocalDate version) {
    this.version = version;
  }

  public static TermsVersion from(LocalDate version) {
    return new TermsVersion(version);
  }

  public boolean isEffectiveOn(LocalDate referenceDate) {
    return !version.isAfter(referenceDate);
  }

  public boolean isAfter(TermsVersion other) {
    return this.version.isAfter(other.version);
  }
}
