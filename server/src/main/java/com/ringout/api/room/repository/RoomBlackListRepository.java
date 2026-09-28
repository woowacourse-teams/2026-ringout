package com.ringout.api.room.repository;

import com.ringout.api.room.domain.Room;
import com.ringout.api.room.domain.RoomBlackList;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomBlackListRepository extends JpaRepository<RoomBlackList, Long> {

    // TODO: 아직 추방 기능이 구현되지 않았기 때문에 hard deleted로 구현했습니다.
    void deleteAllByRoom(Room room);
}
