package com.ringout.api.admin.repository;

import com.ringout.api.alarmoccurrence.domain.AlarmOccurrence;
import java.time.LocalDateTime;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AdminRoomTimelineRepository extends Repository<AlarmOccurrence, Long> {
    @EntityGraph(attributePaths = {"user", "user.image"})
    @Query("""
        select o from AlarmOccurrence o
        join RoomUser ru on ru.user = o.user
        where ru.room.id = :roomId and ru.deletedAt is null
          and o.deletedAt is null and o.user.deletedAt is null
          and (:userId is null or o.user.id = :userId)
          and o.startedAt >= :start and o.startedAt < :end
        order by o.startedAt asc, o.id asc
        """)
    Slice<AlarmOccurrence> findTimeline(@Param("roomId") Long roomId,
            @Param("userId") Long userId, @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end, Pageable pageable);
}
