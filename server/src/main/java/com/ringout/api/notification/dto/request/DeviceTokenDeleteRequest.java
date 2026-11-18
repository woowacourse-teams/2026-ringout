package com.ringout.api.notification.dto.request;

import jakarta.validation.constraints.NotBlank;

public record DeviceTokenDeleteRequest(
    @NotBlank
    String fcmToken
) {

}
