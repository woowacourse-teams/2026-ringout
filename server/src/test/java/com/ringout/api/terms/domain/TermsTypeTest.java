package com.ringout.api.terms.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TermsTypeTest {

  @Test
  void 필수_타입을_모두_포함하면_참이다() {
    boolean result = TermsType.includeAllRequired(List.of(TermsType.SERVICE, TermsType.PRIVACY));

    assertThat(result).isTrue();
  }

  @Test
  void 필수_타입_중_일부가_빠지면_거짓이다() {
    boolean result = TermsType.includeAllRequired(List.of(TermsType.SERVICE));

    assertThat(result).isFalse();
  }

  @Test
  void required는_모든_약관_타입을_반환한다() {
    assertThat(TermsType.required()).containsExactlyInAnyOrder(TermsType.SERVICE, TermsType.PRIVACY);
  }
}
