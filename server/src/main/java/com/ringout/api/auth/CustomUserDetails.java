package com.ringout.api.auth;

public record CustomUserDetails(
    Long userId,
    Role role
) {
}
