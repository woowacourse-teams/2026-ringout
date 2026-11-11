package com.ringout.api.alarm.repository;

import com.ringout.api.alarm.domain.Alarm;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    @Query("""
        select alarm
        from Alarm alarm
        where alarm.user.id = :userId
          and alarm.deletedAt is null
        order by alarm.setting.schedule.ringingRule.alarmTime.value asc, alarm.clientAlarmId.value asc
        """)
    List<Alarm> findOwnedAlarms(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select alarm
        from Alarm alarm
        where alarm.user.id = :userId
          and alarm.clientAlarmId.value = :alarmId
        """)
    Optional<Alarm> findOwnedAlarmForUpdate(@Param("userId") Long userId, @Param("alarmId") String alarmId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(nativeQuery = true, value = """
        INSERT INTO alarm
            (alarm_id, user_id, alarm_time, alarm_days, repeat_minutes, destination_name,
             destination_latitude, destination_longitude, alarm_version, created_at, updated_at)
        VALUES
            (:alarmId, :userId, :alarmTime, :alarmDays, :repeatMinutes, :destinationName,
             :latitude, :longitude, :alarmVersion, :now, :now)
        AS requested
        ON DUPLICATE KEY UPDATE
            alarm_time = IF(requested.alarm_version > alarm.alarm_version,
                requested.alarm_time, alarm.alarm_time),
            alarm_days = IF(requested.alarm_version > alarm.alarm_version,
                requested.alarm_days, alarm.alarm_days),
            repeat_minutes = IF(requested.alarm_version > alarm.alarm_version,
                requested.repeat_minutes, alarm.repeat_minutes),
            destination_name = IF(requested.alarm_version > alarm.alarm_version,
                requested.destination_name, alarm.destination_name),
            destination_latitude = IF(requested.alarm_version > alarm.alarm_version,
                requested.destination_latitude, alarm.destination_latitude),
            destination_longitude = IF(requested.alarm_version > alarm.alarm_version,
                requested.destination_longitude, alarm.destination_longitude),
            updated_at = IF(requested.alarm_version > alarm.alarm_version,
                requested.updated_at, alarm.updated_at),
            alarm_version = IF(requested.alarm_version > alarm.alarm_version,
                requested.alarm_version, alarm.alarm_version)
        """)
    void upsertOwnedAlarm(
        @Param("userId") Long userId,
        @Param("alarmId") String alarmId,
        @Param("alarmTime") LocalTime alarmTime,
        @Param("alarmDays") int alarmDays,
        @Param("repeatMinutes") int repeatMinutes,
        @Param("destinationName") String destinationName,
        @Param("latitude") double latitude,
        @Param("longitude") double longitude,
        @Param("alarmVersion") Long alarmVersion,
        @Param("now") LocalDateTime now
    );
}
