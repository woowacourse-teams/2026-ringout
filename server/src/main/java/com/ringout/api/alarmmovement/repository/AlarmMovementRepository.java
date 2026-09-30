package com.ringout.api.alarmmovement.repository;

import com.ringout.api.alarmmovement.domain.AlarmMovement;
import com.ringout.api.alarm.domain.ActiveAlarm;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlarmMovementRepository extends JpaRepository<AlarmMovement, Long> {

    Optional<AlarmMovement> findByActiveAlarm(ActiveAlarm activeAlarm);

    @Query("""
        select alarmMovement
        from AlarmMovement alarmMovement
        join fetch alarmMovement.activeAlarm activeAlarm
        where activeAlarm in :activeAlarms
          and alarmMovement.deletedAt is null
        """)
    List<AlarmMovement> findActiveByActiveAlarmIn(@Param("activeAlarms") List<ActiveAlarm> activeAlarms);
}
