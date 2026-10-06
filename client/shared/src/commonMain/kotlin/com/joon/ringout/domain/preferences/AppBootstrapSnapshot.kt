package com.joon.ringout.domain.preferences

import com.joon.ringout.ThemeMode
import com.joon.ringout.domain.firstlaunch.FirstLaunchStatus

data class AppBootstrapSnapshot(
    val themeMode: ThemeMode? = null,
    val firstLaunchStatus: FirstLaunchStatus,
)
