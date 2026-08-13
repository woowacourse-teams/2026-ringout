package com.ringout.api.terms.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "terms",
    indexes = {
        @Index(name = "idx_type_version", columnList = "type, version", unique = true)
    })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TermsType type;

  @Embedded
  private TermsVersion version;

  public boolean isEffectiveOn(LocalDate referenceDate) {
    return version.isEffectiveOn(referenceDate);
  }

  public boolean isNewerThan(Terms other) {
    return this.version.isAfter(other.version);
  }

  @Builder(access = AccessLevel.PRIVATE)
  public Terms(TermsType type, TermsVersion version) {
    this.type = type;
    this.version = version;
  }

  public static Terms of(TermsType type, TermsVersion version) {
    return Terms.builder().type(type).version(version).build();
  }
}
