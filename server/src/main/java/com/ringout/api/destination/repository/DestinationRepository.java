package com.ringout.api.destination.repository;

import com.ringout.api.destination.domain.Destination;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DestinationRepository extends JpaRepository<Destination, Long> {

    @Query("""
        select destination
        from Destination destination
        where destination.member.id = :userId
        order by destination.id asc
        """)
    List<Destination> findOwnedDestinations(@Param("userId") Long userId);
}
