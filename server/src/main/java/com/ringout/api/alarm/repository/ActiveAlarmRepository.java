package com.ringout.api.alarm.repository;

import com.ringout.api.alarm.domain.ActiveAlarm;
import com.ringout.api.room.domain.RoomUser;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActiveAlarmRepository extends JpaRepository<ActiveAlarm, Long> {

    @Query("select activeAlarm from ActiveAlarm activeAlarm where activeAlarm.id = :activeAlarmId and activeAlarm.deletedAt is null")
    Optional<ActiveAlarm> findActiveById(@Param("activeAlarmId") Long activeAlarmId);

    @Query("""
        select activeAlarm
        from ActiveAlarm activeAlarm
        join fetch activeAlarm.alarm alarm
        join RoomUser roomUser on roomUser.user = alarm.user
        where roomUser.room.id = :roomId
          and roomUser.deletedAt is null
          and alarm.deletedAt is null
          and activeAlarm.deletedAt is null
        order by activeAlarm.id desc
        """)
    List<ActiveAlarm> findActiveByRoomIdOrderByIdDesc(@Param("roomId") Long roomId);
}
