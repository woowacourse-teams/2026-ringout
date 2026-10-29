package com.joon.ringout.push

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.joon.ringout.domain.push.RoomActivityPushRequest
import com.joon.ringout.domain.push.RoomActivityPushRequestState
import com.joon.ringout.domain.push.RoomActivityPushTarget
import java.util.UUID

/** Holds a notification click through bootstrap, Activity recreation, and navigation readiness. */
internal class RoomActivityPushClickViewModel(
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val restoredRequest = restoredRequest()
    private val requestState = RoomActivityPushRequestState(
        pendingRequest = restoredRequest,
        lastAcceptedRequestId =
            savedState.get<String>(LastAcceptedRequestIdKey) ?: restoredRequest?.requestId,
    )

    var pendingRequest by mutableStateOf(requestState.pendingRequest)
        private set

    /** Accepts both app-created notification intents and FCM's launcher extras. */
    fun receive(intent: Intent, isNewIntent: Boolean, preservePendingRequest: Boolean = false) {
        val extras = intent.extras
        val customRequestId = extras.string(PushNotifications.EXTRA_REQUEST_ID)
        val hasRoomData = extras?.let {
            it.containsKey(RoomActivityPushTarget.RoomIdKey) ||
                it.containsKey(RoomActivityPushTarget.ActivityDateKey)
        } == true

        if (customRequestId == null && !hasRoomData && !isNewIntent) return
        // An alarm/launcher return while gated must not consume a notification awaiting app access.
        if (preservePendingRequest && customRequestId == null && !hasRoomData) return

        val externalFingerprint = if (customRequestId == null && hasRoomData) {
            externalFingerprint(intent)
        } else {
            null
        }
        if (externalFingerprint != null &&
            externalFingerprint == savedState.get<String>(LastExternalFingerprintKey)
        ) return

        val requestId = customRequestId
            ?: externalMessageId(intent)?.let { "fcm:$it" }
            ?: UUID.randomUUID().toString()
        intent.putExtra(PushNotifications.EXTRA_REQUEST_ID, requestId)

        val target = if (hasRoomData) {
            RoomActivityPushTarget.parse(
                buildMap {
                    extras.string(RoomActivityPushTarget.RoomIdKey)?.let { value ->
                        put(RoomActivityPushTarget.RoomIdKey, value)
                    }
                    extras.string(RoomActivityPushTarget.ActivityDateKey)?.let { value ->
                        put(RoomActivityPushTarget.ActivityDateKey, value)
                    }
                },
            )
        } else {
            null
        }

        if (requestState.accept(requestId, target)) {
            pendingRequest = requestState.pendingRequest
            savedState[LastAcceptedRequestIdKey] = requestState.lastAcceptedRequestId
            savedState[LastExternalFingerprintKey] = externalFingerprint
            persistPendingRequest()
        }
    }

    fun acknowledge(requestId: String) {
        if (!requestState.acknowledge(requestId)) return
        pendingRequest = requestState.pendingRequest
        persistPendingRequest()
    }

    private fun restoredRequest(): RoomActivityPushRequest? {
        val requestId = savedState.get<String>(PendingRequestIdKey) ?: return null
        val target = RoomActivityPushTarget.parse(
            mapOf(
                RoomActivityPushTarget.RoomIdKey to
                    (savedState.get<String>(PendingRoomIdKey) ?: return null),
                RoomActivityPushTarget.ActivityDateKey to
                    (savedState.get<String>(PendingActivityDateKey) ?: return null),
            ),
        ) ?: return null
        return RoomActivityPushRequest(requestId, target)
    }

    private fun persistPendingRequest() {
        val request = requestState.pendingRequest
        savedState[PendingRequestIdKey] = request?.requestId
        savedState[PendingRoomIdKey] = request?.target?.roomId
        savedState[PendingActivityDateKey] = request?.target?.activityDate
    }

    private fun externalFingerprint(intent: Intent): String {
        val extras = intent.extras
        val messageId = externalMessageId(intent)
        if (messageId != null) return "message:$messageId"

        val sentTime = runCatching { extras?.getLong("google.sent_time") }
            .getOrNull()
            ?.toString()
            .orEmpty()
        val roomId = extras.string(RoomActivityPushTarget.RoomIdKey).orEmpty()
        val activityDate = extras.string(RoomActivityPushTarget.ActivityDateKey).orEmpty()
        return "payload:$roomId:$activityDate:$sentTime"
    }

    private fun externalMessageId(intent: Intent): String? =
        intent.extras.string("google.message_id")

    private fun Bundle?.string(key: String): String? =
        runCatching { this?.getString(key) }.getOrNull()

    private companion object {
        const val LastAcceptedRequestIdKey = "roomActivityPush.lastAcceptedRequestId"
        const val LastExternalFingerprintKey = "roomActivityPush.lastExternalFingerprint"
        const val PendingRequestIdKey = "roomActivityPush.pendingRequestId"
        const val PendingRoomIdKey = "roomActivityPush.pendingRoomId"
        const val PendingActivityDateKey = "roomActivityPush.pendingActivityDate"
    }
}
