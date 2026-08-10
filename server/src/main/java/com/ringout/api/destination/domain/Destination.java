package com.ringout.api.destination.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "destination"
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Destination extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Embedded
  private DestinationAlias alias;

  @Embedded
  private Coordinate coordinate;

  private Long memberId;
}
