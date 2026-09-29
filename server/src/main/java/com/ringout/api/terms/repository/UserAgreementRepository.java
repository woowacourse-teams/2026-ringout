package com.ringout.api.terms.repository;

import com.ringout.api.terms.domain.UserAgreement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {

  boolean existsByUserIdAndTermsId(Long userId, Long termsId);

  @Query("""
      select userAgreement from UserAgreement userAgreement
      join fetch userAgreement.terms
      where userAgreement.user.id = :userId
        and userAgreement.deletedAt is null
      """)
  List<UserAgreement> findActiveWithTermsByUserId(@Param("userId") Long userId);
}
