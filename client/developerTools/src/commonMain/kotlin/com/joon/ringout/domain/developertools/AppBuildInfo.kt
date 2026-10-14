package com.joon.ringout.domain.developertools

/** Immutable snapshot of the configuration actually used by this installed app. */
data class AppBuildInfo(
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val apiBaseUrl: String,
) {
    val serverEnvironment: String
        get() = when (apiBaseUrl.trimEnd('/')) {
            "https://dev-api.ringout.my" -> "개발"
            "https://api.ringout.my" -> "운영"
            else -> "기타"
        }
}
