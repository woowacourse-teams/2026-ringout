package com.ringout.api.destination.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.status.DestinationErrorStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

  public void update(String alias, Double latitude, Double longitude) {
    updateAlias(alias);
    updateCoordinate(latitude, longitude);
  }

  public boolean isOwnedBy(Long userId) {
    return this.userId.equals(userId);
  }

  private void updateAlias(String alias) {
    if (alias == null) {
      return;
    }

    try {
      this.alias = DestinationAlias.from(alias);
    } catch (GeneralException exception) {
      throw new GeneralException(DestinationErrorStatus.DESTINATION_UPDATE_REQUEST_INVALID);
    }
  }

  private void updateCoordinate(Double latitude, Double longitude) {
    if (latitude == null && longitude == null) {
      return;
    }

    Double updatedLatitude = this.coordinate.getLatitude();
    if (latitude != null) {
      updatedLatitude = latitude;
    }

    Double updatedLongitude = this.coordinate.getLongitude();
    if (longitude != null) {
      updatedLongitude = longitude;
    }

    try {
      this.coordinate = Coordinate.of(updatedLatitude, updatedLongitude);
    } catch (GeneralException exception) {
      throw new GeneralException(DestinationErrorStatus.DESTINATION_UPDATE_REQUEST_INVALID);
    }
  }
}
