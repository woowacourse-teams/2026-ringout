package com.ringout.api.room.repository;

import com.ringout.api.room.domain.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("select room from Room room where room.id = :roomId and room.deletedAt is null")
    Optional<Room> findActiveById(@Param("roomId") Long roomId);

    @Query("""
        select room
        from Room room
        left join fetch room.image
        where room.deletedAt is null
        order by room.latestActivityAt desc, room.id asc
        """)
    List<Room> findAllActiveOrderByLatestActivityAtDescIdAsc();

    @Query(value = """
        select *
        from room
        where deleted_at is null
          and (activity_days & :dayMask) != 0
          and hour(activity_time) = :hour
          and minute(activity_time) = :minute
        """, nativeQuery = true)
    List<Room> findActiveByActivityDayAndTime(
        @Param("dayMask") int dayMask,
        @Param("hour") int hour,
        @Param("minute") int minute
    );
}
