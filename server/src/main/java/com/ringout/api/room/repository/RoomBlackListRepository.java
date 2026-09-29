package com.ringout.api.room.repository;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomBlackList;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoomBlackListRepository extends JpaRepository<RoomBlackList, Long> {

    @Query("select roomBlackList from RoomBlackList roomBlackList "
        + "where roomBlackList.room = :room and roomBlackList.deletedAt is null")
    List<RoomBlackList> findActiveByRoom(Room room);
}
