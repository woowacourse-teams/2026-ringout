package com.ringout.api.room.repository;

import com.ringout.api.room.domain.RoomUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomUserRepository extends JpaRepository<RoomUser, Long> {

    @Query("""
        select roomUser
        from RoomUser roomUser
        where roomUser.room.id = :roomId
          and roomUser.user.id = :userId
          and roomUser.deletedAt is null
        """)
    Optional<RoomUser> findActiveByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
