package com.ringout.api.terms.repository;

import com.ringout.api.terms.domain.UserAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {

  boolean existsByUserIdAndTermsId(Long userId, Long termsId);
}
