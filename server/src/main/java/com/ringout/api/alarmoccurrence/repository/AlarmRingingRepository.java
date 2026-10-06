package com.ringout.api.alarmoccurrence.repository;

import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlarmRingingRepository extends JpaRepository<AlarmRinging, Long> {

    @Query("""
        select alarmRinging from AlarmRinging alarmRinging
        where alarmRinging.alarmOccurrence.id in :alarmOccurrenceIds
          and alarmRinging.deletedAt is null
        order by alarmRinging.ringingAt asc, alarmRinging.id asc
        """)
    List<AlarmRinging> findActiveByAlarmOccurrenceIds(@Param("alarmOccurrenceIds") List<Long> alarmOccurrenceIds);
}
