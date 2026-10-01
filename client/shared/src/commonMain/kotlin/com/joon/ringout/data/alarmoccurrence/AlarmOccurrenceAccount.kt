package com.joon.ringout.data.alarmoccurrence

import com.joon.ringout.data.network.ApiJson
import com.joon.ringout.domain.auth.AuthSession
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.SecureTokenStorage
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64

@ConsistentCopyVisibility
data class AlarmOccurrenceAccount internal constructor(
    val ownerAccountId: String,
    internal val sessionIdentity: Any,
)

internal class AlarmOccurrenceAccountChanged : CancellationException("알람 기록을 전송하던 로그인 계정이 변경됐습니다.")

internal suspend fun captureAlarmOccurrenceAccount(storage: SecureTokenStorage, session: AuthSession): AlarmOccurrenceAccount? {
    val identity = session.identity.value ?: return null
    if (session.state.value != AuthSessionState.Authenticated) return null
    val token = storage.read()?.accessToken ?: return null
    val owner = alarmOccurrenceTokenOwner(token) ?: return null
    if (session.identity.value !== identity || session.state.value != AuthSessionState.Authenticated) return null
    return AlarmOccurrenceAccount(owner, identity)
}

internal fun AlarmOccurrenceAccount.checkSession(session: AuthSession) {
    if (session.identity.value !== sessionIdentity || session.state.value != AuthSessionState.Authenticated) {
        throw AlarmOccurrenceAccountChanged()
    }
}

internal fun AlarmOccurrenceAccount.checkToken(session: AuthSession, token: String) {
    checkSession(session)
    if (alarmOccurrenceTokenOwner(token) != ownerAccountId) throw AlarmOccurrenceAccountChanged()
}

/** 서버가 검증할 JWT의 userId를 로컬 소유 계정 비교에만 사용한다. 인증 검증을 대체하지 않는다. */
internal fun alarmOccurrenceTokenOwner(token: String): String? = runCatching {
    val parts = token.split('.')
    if (parts.size != 3) return null
    val payload = parts[1].padEnd((parts[1].length + 3) / 4 * 4, '=')
    val claims = ApiJson.parseToJsonElement(Base64.UrlSafe.decode(payload).decodeToString()).jsonObject
    if (claims["tokenType"]?.jsonPrimitive?.contentOrNull != "ACCESS") return null
    val userId = claims["userId"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: return null
    if (claims["sub"]?.jsonPrimitive?.contentOrNull != userId.toString()) return null
    userId.toString()
}.getOrNull()
