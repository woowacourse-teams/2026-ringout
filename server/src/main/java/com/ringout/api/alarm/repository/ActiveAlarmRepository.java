package com.ringout.api.alarm.repository;

import com.ringout.api.alarm.domain.ActiveAlarm;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActiveAlarmRepository extends JpaRepository<ActiveAlarm, Long> {

    @Query("select activeAlarm from ActiveAlarm activeAlarm where activeAlarm.id = :activeAlarmId and activeAlarm.deletedAt is null")
    Optional<ActiveAlarm> findActiveById(@Param("activeAlarmId") Long activeAlarmId);
}
