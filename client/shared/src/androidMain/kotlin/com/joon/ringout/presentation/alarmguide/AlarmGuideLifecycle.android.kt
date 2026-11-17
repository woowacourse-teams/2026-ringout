package com.joon.ringout.presentation.alarmguide

import androidx.activity.ComponentActivity
import androidx.lifecycle.LifecycleOwner

internal actual fun isConfigurationChange(owner: LifecycleOwner): Boolean =
    (owner as? ComponentActivity)?.isChangingConfigurations == true
