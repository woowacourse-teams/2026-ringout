package com.joon.ringout.data.destination

import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class DestinationAccountContext(
    val ownerAccountId: String,
    val sessionIdentity: Any,
)

internal class DestinationAccountChanged : CancellationException("목적지 계정이 변경되거나 로그아웃 중입니다.")

class DestinationAccountProvider(
    private val authSession: AuthSession,
    private val currentVerifiedAccountId: suspend () -> String?,
) {
    val accountIds: Flow<String?> = combine(
        authSession.state,
        authSession.identity,
    ) { state, identity -> state to identity }
        .map { (state, identity) -> resolve(state, identity)?.ownerAccountId }
        .distinctUntilChanged()

    internal suspend fun currentContext(): DestinationAccountContext? =
        resolve(authSession.state.value, authSession.identity.value)

    suspend fun currentAccountId(): String? = currentContext()?.ownerAccountId

    private suspend fun resolve(state: AuthSessionState, identity: Any?): DestinationAccountContext? {
        if (state != AuthSessionState.Authenticated || identity == null) return null
        val accountId = currentVerifiedAccountId() ?: return null
        if (authSession.state.value != AuthSessionState.Authenticated ||
            authSession.identity.value !== identity
        ) return null
        return DestinationAccountContext(accountId, identity)
    }
}

/** Serializes account-bound database acknowledgements with logout cleanup. */
class DestinationSyncSessionGate {
    private val mutex = Mutex()
    private var blockedSessionIdentity: Any? = null

    suspend fun <T> withSession(sessionIdentity: Any, block: suspend () -> T): T = mutex.withLock {
        if (blockedSessionIdentity === sessionIdentity) throw DestinationAccountChanged()
        block()
    }

    suspend fun <T> clearForLogout(sessionIdentity: Any?, clear: suspend () -> T): T = mutex.withLock {
        if (sessionIdentity != null) blockedSessionIdentity = sessionIdentity
        clear()
    }
}
