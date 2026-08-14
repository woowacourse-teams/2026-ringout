package com.ringout.api.auth.dto;

public record SignupResponse(
    String accessToken,
    String refreshToken
) {
}
