package com.ringout.api.pushalarm.repository;

import com.ringout.api.pushalarm.domain.Device;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByFcmToken(String fcmToken);
}
