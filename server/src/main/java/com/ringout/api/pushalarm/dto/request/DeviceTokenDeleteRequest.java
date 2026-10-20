package com.ringout.api.pushalarm.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenDeleteRequest(
    @NotBlank
    String fcmToken
) {

}
