package com.joon.ringout.alarm

import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.data.auth.restoreAuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.getAuthSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

data class IosAlarmMissionAccessResult(
    val restored: Boolean,
    val missionAllowed: Boolean,
    val message: String? = null,
)

/** Restores local secure state for native alarm intents without constructing the service graph. */
object IosAlarmAuthentication {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun restoreMissionAccess(callback: (IosAlarmMissionAccessResult) -> Unit) {
        scope.launch {
            val result = try {
                IosAlarmMissionAccessResult(true, restoreIosAlarmMissionAccess())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                IosAlarmMissionAccessResult(false, false, error.message)
            }
            callback(result)
        }
    }
}

internal suspend fun restoreIosAlarmMissionAccess(): Boolean {
    val session = getAuthSession()
    createSecureTokenStorage().restoreAuthSession(session)
    return session.state.value == AuthSessionState.Authenticated
}
