package com.ringout.api.room.repository;

import com.ringout.api.room.domain.RoomUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomUserRepository extends JpaRepository<RoomUser, Long> {
}
