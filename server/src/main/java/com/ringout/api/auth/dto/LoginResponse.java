package com.ringout.api.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean isNewUser
) {
}
