package com.ringout.api.auth.social;

public record SocialUserInfo(
        SocialProvider provider,
        String providerId,
        String email
) {
}
