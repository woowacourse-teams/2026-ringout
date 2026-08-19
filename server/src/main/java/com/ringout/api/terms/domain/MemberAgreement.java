package com.ringout.api.terms.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.member.domain.Member;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "member_agreement"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAgreement extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "member_id")
  private Member member;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "terms_id")
  private Terms terms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TermsType type;

  @Embedded
  @AttributeOverride(name = "version", column = @Column(name = "version", nullable = false))
  private TermsVersion version;

  @Builder(access = AccessLevel.PRIVATE)
  public MemberAgreement(Member member, Terms terms, TermsType type, TermsVersion version) {
    this.member = member;
    this.terms = terms;
    this.type = type;
    this.version = version;
  }

  public static MemberAgreement of(Member member, Terms terms, TermsType type, TermsVersion version) {
    return MemberAgreement.builder()
        .member(member)
        .terms(terms)
        .type(type)
        .version(version)
        .build();
  }
}
