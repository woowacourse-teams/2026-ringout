package com.joon.ringout.presentation.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.presentation.destination.DestinationCurrentLocationCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

internal class AppLocationCacheViewModel(
    authSession: AuthSession,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {
    val destinationCurrentLocationCache = DestinationCurrentLocationCache()

    private var sessionIdentity = authSession.identity.value

    init {
        (coroutineScope ?: viewModelScope).launch(start = CoroutineStart.UNDISPATCHED) {
            authSession.identity.collect { identity ->
                if (sessionIdentity !== identity) {
                    destinationCurrentLocationCache.clear()
                    sessionIdentity = identity
                }
            }
        }
    }
}
