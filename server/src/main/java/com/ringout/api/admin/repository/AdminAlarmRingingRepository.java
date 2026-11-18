package com.ringout.api.admin.repository;

import com.ringout.api.alarmoccurrence.domain.AlarmRinging;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminAlarmRingingRepository extends Repository<AlarmRinging, Long> {
    @Query("""
        select r from AlarmRinging r
        where r.alarmOccurrence.id in :ids and r.deletedAt is null
        order by r.ringingAt asc, r.id asc
        """)
    List<AlarmRinging> findActiveByOccurrenceIds(@Param("ids") List<Long> ids);
}
