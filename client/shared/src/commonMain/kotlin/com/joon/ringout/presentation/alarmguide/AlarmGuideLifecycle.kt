package com.joon.ringout.presentation.alarmguide

import androidx.lifecycle.LifecycleOwner

internal expect fun isConfigurationChange(owner: LifecycleOwner): Boolean
