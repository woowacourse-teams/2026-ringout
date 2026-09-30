package com.ringout.api.room.repository;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomBlackList;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomBlackListRepository extends JpaRepository<RoomBlackList, Long> {

    @Query("select roomBlackList from RoomBlackList roomBlackList "
        + "where roomBlackList.room = :room and roomBlackList.deletedAt is null")
    List<RoomBlackList> findActiveByRoom(Room room);

    @Query("""
        select case when count(roomBlackList) > 0 then true else false end
        from RoomBlackList roomBlackList
        where roomBlackList.room.id = :roomId
          and roomBlackList.user.id = :userId
          and roomBlackList.deletedAt is null
        """)
    boolean existsActiveByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
