package com.ringout.api.notification.dto.request;

import com.ringout.api.notification.domain.Platform;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRegisterRequest(
    @NotNull
    Platform platform,
    @NotNull
    String fcmToken
) {

}
