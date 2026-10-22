package com.joon.ringout.data.push

import com.joon.ringout.domain.push.PushTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AndroidFcmTokenSource : PushTokenSource {
    private val token = MutableStateFlow<String?>(null)
    override val latestFcmToken: StateFlow<String?> = token.asStateFlow()

    @Synchronized
    fun update(fcmToken: String) {
        if (fcmToken.isNotBlank()) token.value = fcmToken
    }

    @Synchronized
    fun updateInitial(fcmToken: String) {
        // A refresh callback may arrive while the startup fetch is still pending.
        if (token.value == null) update(fcmToken)
    }
}
