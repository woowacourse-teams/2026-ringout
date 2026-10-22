package com.ringout.api.notification.repository;

import com.ringout.api.notification.domain.Device;
import com.ringout.api.notification.repository.dto.RoomMemberDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByFcmToken(String fcmToken);

    @Query("""
        select roomUser.room.id, device
        from RoomUser roomUser
        join Device device on device.user = roomUser.user
        where roomUser.room.id in :roomIds
          and roomUser.deletedAt is null
        """)
    List<RoomMemberDevice> findActiveRoomMemberDevices(@Param("roomIds") List<Long> roomIds);
}
