package com.ringout.api.terms.repository;

import com.ringout.api.terms.domain.MemberAgreement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberAgreementRepository extends JpaRepository<MemberAgreement, Long> {

  boolean existsByMemberIdAndTermsId(Long memberId, Long termsId);

  void deleteAllByMemberId(Long memberId);
}
