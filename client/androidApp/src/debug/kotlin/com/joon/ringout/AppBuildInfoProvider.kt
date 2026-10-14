package com.joon.ringout

import com.joon.ringout.data.network.ApiConfig
import com.joon.ringout.domain.developertools.AppBuildInfo

/** Assemble platform and network configuration outside Presentation. */
internal fun currentAppBuildInfo() = AppBuildInfo(
    versionName = BuildConfig.VERSION_NAME,
    versionCode = BuildConfig.VERSION_CODE,
    buildType = BuildConfig.BUILD_TYPE,
    apiBaseUrl = ApiConfig.BASE_URL,
)
