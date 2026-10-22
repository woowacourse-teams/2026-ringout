package com.joon.ringout.domain.push

import com.joon.ringout.domain.auth.AuthSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Platform identity translated to the backend's wire value by the data layer. */
enum class PushPlatform {
    Android,
    Ios,
}

/** A token eligible for server registration after the user session is authenticated. */
data class PushTokenRegistrationCandidate(
    val platform: PushPlatform,
    val fcmToken: String,
)

/** Current authentication, token readiness, and latest server registration result. */
enum class PushTokenRegistrationStatus {
    WaitingForAuthentication,
    WaitingForToken,
    Ready,
    Registering,
    Succeeded,
    Failed,
}

/** Provides the current FCM token and publishes changes without exposing platform SDK types. */
interface PushTokenSource {
    val latestFcmToken: StateFlow<String?>
}

/** Port for registering a prepared token with the backend. */
fun interface PushTokenRegistrar {
    suspend fun register(candidate: PushTokenRegistrationCandidate)
}

/**
 * Holds the latest platform token, exposes it only to an authenticated session, and tracks registration progress.
 */
class PushTokenRegistrationReadiness(
    private val platform: PushPlatform,
) {
    private var isAuthenticated = false
    private var latestFcmToken: String? = null
    private val mutableCandidate = MutableStateFlow<PushTokenRegistrationCandidate?>(null)
    private val mutableRegistrationStatus =
        MutableStateFlow(PushTokenRegistrationStatus.WaitingForAuthentication)

    val candidate: StateFlow<PushTokenRegistrationCandidate?> = mutableCandidate.asStateFlow()
    val registrationStatus: StateFlow<PushTokenRegistrationStatus> =
        mutableRegistrationStatus.asStateFlow()

    fun updateAuthSession(state: AuthSessionState) {
        isAuthenticated = state == AuthSessionState.Authenticated
        refreshCandidate()
    }

    fun updateFcmToken(token: String?) {
        latestFcmToken = token?.takeIf(String::isNotBlank)
        refreshCandidate()
    }

    private fun refreshCandidate() {
        val candidate = if (isAuthenticated) {
            latestFcmToken?.let { token ->
                PushTokenRegistrationCandidate(
                    platform = platform,
                    fcmToken = token,
                )
            }
        } else {
            null
        }
        if (candidate != mutableCandidate.value) {
            mutableCandidate.value = candidate
            mutableRegistrationStatus.value = when {
                !isAuthenticated -> PushTokenRegistrationStatus.WaitingForAuthentication
                candidate == null -> PushTokenRegistrationStatus.WaitingForToken
                else -> PushTokenRegistrationStatus.Ready
            }
        } else if (candidate == null) {
            mutableRegistrationStatus.value = if (isAuthenticated) {
                PushTokenRegistrationStatus.WaitingForToken
            } else {
                PushTokenRegistrationStatus.WaitingForAuthentication
            }
        }
    }

    fun markRegistrationInProgress(candidate: PushTokenRegistrationCandidate) {
        if (candidate == mutableCandidate.value) {
            mutableRegistrationStatus.value = PushTokenRegistrationStatus.Registering
        }
    }

    fun markRegistrationSucceeded(candidate: PushTokenRegistrationCandidate) {
        if (candidate == mutableCandidate.value) {
            mutableRegistrationStatus.value = PushTokenRegistrationStatus.Succeeded
        }
    }

    fun markRegistrationFailed(candidate: PushTokenRegistrationCandidate) {
        if (candidate == mutableCandidate.value) {
            mutableRegistrationStatus.value = PushTokenRegistrationStatus.Failed
        }
    }
}
