package com.ringout.api.terms.support;

import com.ringout.api.terms.domain.Terms;
import com.ringout.api.terms.domain.TermsType;
import com.ringout.api.terms.domain.TermsVersion;
import com.ringout.api.terms.repository.TermsRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TermsSeeder implements ApplicationRunner {

  private static final List<Terms> SEED_TERMS = List.of(
      Terms.of(TermsType.SERVICE, TermsVersion.from(LocalDate.of(2026, 8, 13))),
      Terms.of(TermsType.PRIVACY, TermsVersion.from(LocalDate.of(2026, 8, 13)))
  );

  private final TermsRepository termsRepository;

  @Override
  public void run(ApplicationArguments args) throws Exception {
    SEED_TERMS.stream()
        .filter(terms -> !termsRepository.existsByTypeAndVersion(terms.getType(), terms.getVersion()))
        .forEach(termsRepository::save);
  }
}
