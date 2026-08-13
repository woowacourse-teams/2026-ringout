package com.ringout.api.terms.repository;

import com.ringout.api.terms.domain.Terms;
import com.ringout.api.terms.domain.TermsType;
import com.ringout.api.terms.domain.TermsVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsRepository extends JpaRepository<Terms, Long> {

  List<Terms> findByType(TermsType type);

  boolean existsByTypeAndVersion(TermsType type, TermsVersion version);
}
