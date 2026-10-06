package com.joon.ringout.analytics

import android.content.Context
import com.joon.ringout.data.alarmoccurrence.alarmOccurrenceTokenOwner
import com.joon.ringout.data.auth.local.createSecureTokenStorage
import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.domain.auth.getAuthSession

internal fun createRoomMembershipAnalytics(context: Context): RoomMembershipAnalytics {
    val preferences = context.applicationContext.getSharedPreferences("room_analytics", Context.MODE_PRIVATE)
    val tokens = createSecureTokenStorage(context)
    return RoomMembershipAnalytics(object : RoomAnalyticsStorage {
        override fun read(key: String) = preferences.getString(key, null)
        override fun write(key: String, value: String) { check(preferences.edit().putString(key, value).commit()) }
        override fun <T> locked(block: () -> T): T = synchronized(RoomAnalyticsLock, block)
    }, currentOwner = {
        when (getAuthSession().state.value) {
            AuthSessionState.Unauthenticated -> RoomMembershipAnalytics.GuestOwner
            AuthSessionState.ReauthenticationRequired -> null
            else -> tokens.readSnapshot()?.accessToken?.let(::alarmOccurrenceTokenOwner)
        }
    })
}

private val RoomAnalyticsLock = Any()
