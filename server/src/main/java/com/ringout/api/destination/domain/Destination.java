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
    private static final long INITIAL_DESTINATION_VERSION = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "destination_id", nullable = false, length = MAX_DESTINATION_ID_LENGTH)
    private String destinationId;

    @Embedded
    private DestinationAlias alias;

    @Embedded
    private Coordinate coordinate;

    @Column(name = "destination_version", nullable = false)
    private Long destinationVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private Destination(User user, String destinationId, DestinationAlias alias, Coordinate coordinate,
        Long destinationVersion) {
        this.user = user;
        this.destinationId = destinationId;
        this.alias = alias;
        this.coordinate = coordinate;
        this.destinationVersion = destinationVersion;
    }

    public static Destination create(User user, String destinationId, DestinationAlias alias, Coordinate coordinate) {
        return create(user, destinationId, alias, coordinate, INITIAL_DESTINATION_VERSION);
    }

    public static Destination create(User user, String destinationId, DestinationAlias alias, Coordinate coordinate,
        Long destinationVersion) {
        if (!isValidDestinationId(destinationId)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_ID_INVALID);
        }
        if (!isValidDestinationVersion(destinationVersion)) {
            throw new GeneralException(DestinationErrorStatus.DESTINATION_VERSION_INVALID);
        }

        return new Destination(user, destinationId, alias, coordinate, destinationVersion);
    }

    public static boolean isValidDestinationId(String destinationId) {
        return destinationId != null && !destinationId.isBlank() && !destinationId.contains(" ")
            && destinationId.length() <= MAX_DESTINATION_ID_LENGTH;
    }

    public static boolean isValidDestinationVersion(Long destinationVersion) {
        return destinationVersion != null;
    }
}
