package com.joon.ringout.presentation.login

internal actual val PlatformSocialLoginProviders: List<SocialLoginProvider> = listOf(
    SocialLoginProvider.Apple,
    SocialLoginProvider.Google,
    SocialLoginProvider.Kakao,
)
