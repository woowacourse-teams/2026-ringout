package com.ringout.api.destination.repository;

import com.ringout.api.destination.domain.Destination;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationRepository extends JpaRepository<Destination, Long> {
}
