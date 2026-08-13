package com.ringout.api.destination.domain;

import com.ringout.api.common.BaseEntity;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
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

  @Column(nullable = false)
  private Long userId;

  private Destination(Long userId, DestinationAlias alias, Coordinate coordinate) {
    this.userId = userId;
    this.alias = alias;
    this.coordinate = coordinate;
  }

  public static Destination create(Long userId, DestinationAlias alias, Coordinate coordinate) {
    return new Destination(userId, alias, coordinate);
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }
}
