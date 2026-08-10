package com.ringout.api.terms.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "member_agreement",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_member_agreement_member_terms",
        columnNames = {"member_id", "terms_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAgreement extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long memberId;

  private Long termsId;

  public MemberAgreement(Long memberId, Long termsId) {
    this.memberId = memberId;
    this.termsId = termsId;
  }
}
