package com.ringout.api.terms.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "member_agreement"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAgreement extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Long memberId;

  private Long termsId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TermsType type;

  @Embedded
  @AttributeOverride(name = "version", column = @Column(name = "version", nullable = false))
  private TermsVersion version;

  public MemberAgreement(Long memberId, Long termsId) {
    this.memberId = memberId;
    this.termsId = termsId;
  }

  @Builder(access = AccessLevel.PRIVATE)
  public MemberAgreement(Long memberId, Long termsId, TermsType type, TermsVersion version) {
    this.memberId = memberId;
    this.termsId = termsId;
    this.type = type;
    this.version = version;
  }

  public static MemberAgreement of(Long memberId, Long termsId, TermsType type, TermsVersion version) {
    return MemberAgreement.builder()
        .memberId(memberId)
        .termsId(termsId)
        .type(type)
        .version(version)
        .build();
  }
}
