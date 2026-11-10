package com.ringout.api.destination.repository;

import com.ringout.api.destination.domain.Destination;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DestinationRepository extends JpaRepository<Destination, Long> {

    @Query("""
        select destination
        from Destination destination
        where destination.user.id = :userId
        order by destination.id asc
        """)
    List<Destination> findOwnedDestinations(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select destination
        from Destination destination
        where destination.user.id = :userId
          and destination.destinationId = :destinationId
        """)
    Optional<Destination> findOwnedDestinationForUpdate(
        @Param("userId") Long userId,
        @Param("destinationId") String destinationId
    );

    @Modifying
    @Query("""
        delete from Destination destination
        where destination.user.id = :userId
          and destination.destinationId = :destinationId
        """)
    void deleteOwnedDestination(@Param("userId") Long userId, @Param("destinationId") String destinationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
        INSERT INTO destination
            (destination_id, user_id, alias, latitude, longitude, destination_version, created_at, updated_at)
        VALUES
            (:destinationId, :userId, :alias, :latitude, :longitude, :destinationVersion, :now, :now)
        AS requested
        ON DUPLICATE KEY UPDATE
            alias = IF(requested.destination_version > destination.destination_version,
                requested.alias, destination.alias),
            latitude = IF(requested.destination_version > destination.destination_version,
                requested.latitude, destination.latitude),
            longitude = IF(requested.destination_version > destination.destination_version,
                requested.longitude, destination.longitude),
            updated_at = IF(requested.destination_version > destination.destination_version,
                requested.updated_at, destination.updated_at),
            destination_version = IF(requested.destination_version > destination.destination_version,
                requested.destination_version, destination.destination_version)
        """)
    void upsertOwnedDestination(
        @Param("userId") Long userId,
        @Param("destinationId") String destinationId,
        @Param("alias") String alias,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("destinationVersion") Long destinationVersion,
        @Param("now") LocalDateTime now
    );
}
