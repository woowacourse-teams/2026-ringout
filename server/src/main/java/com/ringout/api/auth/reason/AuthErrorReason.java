package com.ringout.api.auth.reason;

public enum AuthErrorReason {

    EMPTY_SOCIAL_ACCESS_TOKEN("EMPTY_SOCIAL_ACCESS_TOKEN"),
    UNSUPPORTED_PROVIDER("UNSUPPORTED_PROVIDER"),
    SOCIAL_AUTHENTICATION_FAILED("SOCIAL_AUTHENTICATION_FAILED"),
    SOCIAL_LOGIN_CLIENT_NOT_FOUND("SOCIAL_LOGIN_CLIENT_NOT_FOUND"),
    INVALID_SIGNUP_TOKEN("INVALID_SIGNUP_TOKEN"),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN"),
    REFRESH_TOKEN_USER_NOT_FOUND("REFRESH_TOKEN_USER_NOT_FOUND");

    private final String reason;

    AuthErrorReason(String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
