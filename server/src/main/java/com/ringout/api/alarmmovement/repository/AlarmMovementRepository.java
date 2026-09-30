package com.ringout.api.alarmmovement.repository;

import com.ringout.api.alarmmovement.domain.AlarmMovement;
import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlarmMovementRepository extends JpaRepository<AlarmMovement, Long> {

    Optional<AlarmMovement> findByAlarmOccurrence(AlarmOccurrence alarmOccurrence);

    @Query("""
        select alarmMovement
        from AlarmMovement alarmMovement
        join fetch alarmMovement.alarmOccurrence alarmOccurrence
        where alarmOccurrence in :alarmOccurrences
          and alarmMovement.deletedAt is null
        """)
    List<AlarmMovement> findActiveByAlarmOccurrenceIn(
        @Param("alarmOccurrences") List<AlarmOccurrence> alarmOccurrences
    );
}
