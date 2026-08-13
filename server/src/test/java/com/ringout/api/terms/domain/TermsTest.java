package com.ringout.api.terms.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TermsTest {

  @Test
  void 시행일이_기준일과_같으면_시행중이다() {
    Terms terms = Terms.of(TermsType.SERVICE, TermsVersion.from(LocalDate.of(2026, 8, 13)));

    assertThat(terms.isEffectiveOn(LocalDate.of(2026, 8, 13))).isTrue();
  }

  @Test
  void 시행일이_기준일_이전이면_시행중이다() {
    Terms terms = Terms.of(TermsType.SERVICE, TermsVersion.from(LocalDate.of(2026, 8, 1)));

    assertThat(terms.isEffectiveOn(LocalDate.of(2026, 8, 13))).isTrue();
  }

  @Test
  void 시행일이_기준일보다_이후면_시행중이_아니다() {
    Terms terms = Terms.of(TermsType.SERVICE, TermsVersion.from(LocalDate.of(2026, 8, 20)));

    assertThat(terms.isEffectiveOn(LocalDate.of(2026, 8, 13))).isFalse();
  }

  @Test
  void 버전이_더_최신이면_isNewerThan이_참이다() {
    Terms older = Terms.of(TermsType.SERVICE, TermsVersion.from(LocalDate.of(2026, 8, 1)));
    Terms newer = Terms.of(TermsType.SERVICE, TermsVersion.from(LocalDate.of(2026, 8, 13)));

    assertThat(newer.isNewerThan(older)).isTrue();
    assertThat(older.isNewerThan(newer)).isFalse();
  }

  @Test
  void 버전이_같으면_isNewerThan이_거짓이다() {
    LocalDate sameVersion = LocalDate.of(2026, 8, 13);
    Terms a = Terms.of(TermsType.SERVICE, TermsVersion.from(sameVersion));
    Terms b = Terms.of(TermsType.SERVICE, TermsVersion.from(sameVersion));

    assertThat(a.isNewerThan(b)).isFalse();
  }
}
