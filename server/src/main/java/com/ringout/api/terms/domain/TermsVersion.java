package com.ringout.api.terms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsVersion {

  @Column(name = "version", nullable = false)
  private LocalDate effectiveDate;

  public TermsVersion(LocalDate effectiveDate) {
    this.effectiveDate = effectiveDate;
  }

  public static TermsVersion from(LocalDate effectiveDate) {
    return new TermsVersion(effectiveDate);
  }
}
