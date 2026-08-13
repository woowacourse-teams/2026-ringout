package com.ringout.api.terms.dto;

import java.time.LocalDate;
import java.util.List;

public record TermsAgreeResponse(
    List<String> agreedTerms,
    LocalDate agreedAt
) {

  public static TermsAgreeResponse of(List<String> agreedTerms, LocalDate agreedAt) {
    return new TermsAgreeResponse(agreedTerms, agreedAt);
  }
}
