package com.ringout.api.auth.social;

import com.ringout.api.auth.social.SocialProvider;

public record SocialUserInfo(
        SocialProvider provider,
        String providerId
) {
}
