package com.ringout.api.pushalarm.dto.request;

import com.ringout.api.pushalarm.domain.Platform;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRegisterRequest(
    @NotNull
    Platform platform,
    @NotNull
    String fcmToken
) {

}
