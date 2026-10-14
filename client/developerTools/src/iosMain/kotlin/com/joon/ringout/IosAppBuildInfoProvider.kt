package com.joon.ringout

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.domain.developertools.AppBuildInfo
import platform.Foundation.NSBundle

/** Read the installed app bundle, not the Shared framework's version metadata. */
internal fun currentIosAppBuildInfo(): AppBuildInfo {
    val appBundle = NSBundle.mainBundle
    return AppBuildInfo(
        versionName = appBundle.buildInfoValue("CFBundleShortVersionString"),
        versionCode = appBundle.buildInfoValue("CFBundleVersion"),
        buildType = appBundle.buildInfoValue("RingoutBuildConfiguration"),
        apiBaseUrl = ApiConfig.BASE_URL,
    )
}

private fun NSBundle.buildInfoValue(key: String): String =
    (objectForInfoDictionaryKey(key) as? String)
        ?.takeIf { it.isNotBlank() && !it.contains("$(") }
        ?: "확인 불가"
