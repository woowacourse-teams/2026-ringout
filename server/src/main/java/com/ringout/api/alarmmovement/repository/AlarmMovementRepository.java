package com.ringout.api.alarmmovement.repository;

import com.ringout.api.alarmmovement.domain.AlarmMovement;
import com.ringout.api.alarm.domain.ActiveAlarm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmMovementRepository extends JpaRepository<AlarmMovement, Long> {

    Optional<AlarmMovement> findByActiveAlarm(ActiveAlarm activeAlarm);
}
