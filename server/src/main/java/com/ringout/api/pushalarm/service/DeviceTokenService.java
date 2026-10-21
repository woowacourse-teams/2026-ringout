package com.ringout.api.pushalarm.service;

import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.pushalarm.domain.Device;
import com.ringout.api.pushalarm.dto.request.DeviceTokenDeleteRequest;
import com.ringout.api.pushalarm.dto.request.DeviceTokenRegisterRequest;
import com.ringout.api.pushalarm.repository.DeviceTokenRepository;
import com.ringout.api.user.domain.User;
import com.ringout.api.user.repository.UserRepository;
import com.ringout.api.user.status.UserErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final UserRepository userRepository;

    @Transactional
    public void register(Long userId, DeviceTokenRegisterRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new GeneralException(UserErrorStatus.USER_NOT_FOUND));

        deviceTokenRepository.findByFcmToken(request.fcmToken())
            .ifPresentOrElse(
                device -> device.changeOwner(user, request.platform()),
                () -> deviceTokenRepository.save(
                    Device.of(user, request.fcmToken(), request.platform())
                )
            );
    }

    @Transactional
    public void unregister(Long userId, DeviceTokenDeleteRequest request) {
        deviceTokenRepository.findByFcmToken(request.fcmToken())
            .filter(device -> device.isOwnedBy(userId))
            .ifPresent(deviceTokenRepository::delete);
    }
}
