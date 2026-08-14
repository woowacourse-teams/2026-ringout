package com.ringout.api.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String signupToken,
        boolean isNewUser
) {

    public static LoginResponse loggedIn(String accessToken, String refreshToken) {
        return new LoginResponse(accessToken, refreshToken, null, false);
    }

    public static LoginResponse signupRequired(String signupToken) {
        return new LoginResponse(null, null, signupToken, true);
    }
}
