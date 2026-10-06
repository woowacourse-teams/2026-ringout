package com.ringout.api.alarmoccurrence.repository;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlarmOccurrenceRepository extends JpaRepository<AlarmOccurrence, Long> {

    @Query("""
        select alarmOccurrence from AlarmOccurrence alarmOccurrence
        where alarmOccurrence.id = :alarmOccurrenceId
          and alarmOccurrence.deletedAt is null
        """)
    Optional<AlarmOccurrence> findActiveById(@Param("alarmOccurrenceId") Long alarmOccurrenceId);

    @Query("""
        select alarmOccurrence
        from AlarmOccurrence alarmOccurrence
        join RoomUser roomUser on roomUser.user = alarmOccurrence.user
        where roomUser.room.id = :roomId
          and roomUser.deletedAt is null
          and alarmOccurrence.startedAt >= :start
          and alarmOccurrence.startedAt < :end
          and alarmOccurrence.deletedAt is null
        order by alarmOccurrence.startedAt desc, alarmOccurrence.id desc
        """)
    List<AlarmOccurrence> findActiveByRoomIdAndStartedAtBetweenOrderByStartedAtDescIdDesc(
        @Param("roomId") Long roomId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("""
        select alarmOccurrence from AlarmOccurrence alarmOccurrence
        where alarmOccurrence.user.id = :userId
          and alarmOccurrence.startedAt >= :start
          and alarmOccurrence.startedAt < :end
          and alarmOccurrence.deletedAt is null
        order by alarmOccurrence.startedAt asc, alarmOccurrence.id asc
        """)
    List<AlarmOccurrence> findActiveByUserIdAndStartedAtBetween(
        @Param("userId") Long userId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("""
        select distinct alarmOccurrence
        from AlarmOccurrence alarmOccurrence
        join RoomUser roomUser on roomUser.user = alarmOccurrence.user
        left join fetch alarmOccurrence.ringings
        where roomUser.room.id = :roomId
          and roomUser.deletedAt is null
          and alarmOccurrence.startedAt >= :start
          and alarmOccurrence.startedAt < :end
          and alarmOccurrence.deletedAt is null
        order by alarmOccurrence.startedAt asc, alarmOccurrence.id asc
        """)
    List<AlarmOccurrence> findActiveByRoomIdAndStartedAtBetween(
        @Param("roomId") Long roomId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    @Query("""
        select alarmOccurrence from AlarmOccurrence alarmOccurrence
        where alarmOccurrence.user.id = :userId
          and alarmOccurrence.clientAlarmId = :clientAlarmId
          and alarmOccurrence.scheduledAt = :scheduledAt
          and alarmOccurrence.deletedAt is null
        """)
    Optional<AlarmOccurrence> findActiveByUserIdAndClientAlarmIdAndScheduledAt(
        @Param("userId") Long userId,
        @Param("clientAlarmId") String clientAlarmId,
        @Param("scheduledAt") LocalDateTime scheduledAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select alarmOccurrence from AlarmOccurrence alarmOccurrence
        where alarmOccurrence.occurrenceUuid = :occurrenceUuid
          and alarmOccurrence.deletedAt is null
        """)
    Optional<AlarmOccurrence> findActiveByOccurrenceUuidForUpdate(@Param("occurrenceUuid") String occurrenceUuid);
}
