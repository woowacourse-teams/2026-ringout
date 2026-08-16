package com.ringout.api.auth.social;

public interface SocialLoginClient {

    SocialProvider supports();

    SocialUserInfo authenticate(String token);
}
