package com.ringout.api.terms.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TermsVersionTest {

  @Test
  void 버전_날짜가_기준일보다_이후가_아니면_시행중이다() {
    TermsVersion version = TermsVersion.from(LocalDate.of(2026, 8, 13));

    assertThat(version.isEffectiveOn(LocalDate.of(2026, 8, 13))).isTrue();
    assertThat(version.isEffectiveOn(LocalDate.of(2026, 8, 14))).isTrue();
  }

  @Test
  void 버전_날짜가_기준일보다_이후면_시행중이_아니다() {
    TermsVersion version = TermsVersion.from(LocalDate.of(2026, 8, 13));

    assertThat(version.isEffectiveOn(LocalDate.of(2026, 8, 12))).isFalse();
  }

  @Test
  void 더_늦은_날짜의_버전이_이후이다() {
    TermsVersion older = TermsVersion.from(LocalDate.of(2026, 8, 1));
    TermsVersion newer = TermsVersion.from(LocalDate.of(2026, 8, 13));

    assertThat(newer.isAfter(older)).isTrue();
    assertThat(older.isAfter(newer)).isFalse();
  }

  @Test
  void 같은_날짜의_버전은_이후가_아니다() {
    LocalDate sameDate = LocalDate.of(2026, 8, 13);

    assertThat(TermsVersion.from(sameDate).isAfter(TermsVersion.from(sameDate))).isFalse();
  }
}
