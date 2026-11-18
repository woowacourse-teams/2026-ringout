package com.ringout.api.auth.dto;

public record LoginResponse(
        Long userId,
        String accessToken,
        String refreshToken,
        String signupToken,
        boolean isNewUser
) {

    public static LoginResponse loggedIn(Long userId, String accessToken, String refreshToken) {
        return new LoginResponse(userId, accessToken, refreshToken, null, false);
    }

    public static LoginResponse signupRequired(String signupToken) {
        return new LoginResponse(null, null, null, signupToken, true);
    }
}
