package com.ringout.api.destination.repository;

import com.ringout.api.destination.domain.Destination;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("""
        select destination
        from Destination destination
        where destination.user.id = :userId
          and destination.destinationId = :destinationId
        """)
    Optional<Destination> findOwnedDestination(
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
}
