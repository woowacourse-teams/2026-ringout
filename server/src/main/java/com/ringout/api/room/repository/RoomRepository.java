package com.ringout.api.room.repository;

import com.ringout.api.room.domain.Room;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, Long> {

    @Query("select room from Room room where room.id = :roomId and room.deletedAt is null")
    Optional<Room> findActiveById(@Param("roomId") Long roomId);
}
