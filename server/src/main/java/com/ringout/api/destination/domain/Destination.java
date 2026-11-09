package com.ringout.api.destination.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.destination.status.DestinationErrorStatus;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "destination",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_destination_user_destination_id",
        columnNames = {"user_id", "destination_id"}
    )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Destination extends BaseEntity {

    private static final int MAX_DESTINATION_ID_LENGTH = 36;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "destination_id", nullable = false, length = MAX_DESTINATION_ID_LENGTH)
    private String destinationId;

    @Embedded
    private DestinationAlias alias;

    @Embedded
    private Coordinate coordinate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Destination(User user, String destinationId, DestinationAlias alias, Coordinate coordinate) {
        this.user = user;
        this.destinationId = destinationId;
        this.alias = alias;
        this.coordinate = coordinate;
    }

    public static Destination create(User user, String destinationId, DestinationAlias alias, Coordinate coordinate) {
        if (!isValidDestinationId(destinationId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ID_INVALID);
        }

        return new Destination(user, destinationId, alias, coordinate);
    }

    public static boolean isValidDestinationId(String destinationId) {
        return destinationId != null && !destinationId.isBlank() && !destinationId.contains(" ")
            && destinationId.length() <= MAX_DESTINATION_ID_LENGTH;
    }

    public void update(String alias, Double latitude, Double longitude) {
        updateAlias(alias);
        updateCoordinate(latitude, longitude);
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
