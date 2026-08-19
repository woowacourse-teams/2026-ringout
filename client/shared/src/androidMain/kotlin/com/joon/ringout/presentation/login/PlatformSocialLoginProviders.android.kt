package com.joon.ringout.presentation.login

internal actual val PlatformSocialLoginProviders: List<SocialLoginProvider> = listOf(
    SocialLoginProvider.Google,
    SocialLoginProvider.Kakao,
)
