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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "terms",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_terms_type_effective_date",
        columnNames = {"type", "effective_date"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private TermsType type;

  @Column(name = "content_url", nullable = false, length = 500)
  private String content;

  @Embedded
  @AttributeOverride(name = "effectiveDate", column = @Column(name = "effective_date", nullable = false))
  private TermsVersion version;
}
