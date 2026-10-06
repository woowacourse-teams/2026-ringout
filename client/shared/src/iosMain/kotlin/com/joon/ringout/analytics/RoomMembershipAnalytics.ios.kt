package com.joon.ringout.analytics

import com.joon.ringout.data.alarmoccurrence.alarmOccurrenceTokenOwner
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.getAuthSession
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults

internal fun createRoomMembershipAnalytics(): RoomMembershipAnalytics {
    val defaults = NSUserDefaults.standardUserDefaults
    val tokens = createSecureTokenStorage()
    return RoomMembershipAnalytics(object : RoomAnalyticsStorage {
        override fun read(key: String) = defaults.stringForKey("room.analytics.$key")
        override fun write(key: String, value: String) { defaults.setObject(value, "room.analytics.$key") }
        override fun <T> locked(block: () -> T): T {
            RoomAnalyticsLock.lock()
            try { return block() } finally { RoomAnalyticsLock.unlock() }
        }
    }, currentOwner = {
        when (getAuthSession().state.value) {
            AuthSessionState.Unauthenticated -> RoomMembershipAnalytics.GuestOwner
            AuthSessionState.ReauthenticationRequired -> null
            else -> tokens.readSnapshot()?.accessToken?.let(::alarmOccurrenceTokenOwner)
        }
    })
}

private val RoomAnalyticsLock = NSLock()
