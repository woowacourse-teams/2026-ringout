package com.joon.ringout.domain.push

import com.joon.ringout.domain.missionhistory.MissionDate

/** A validated destination carried by an Android room activity notification. */
@ConsistentCopyVisibility
data class RoomActivityPushTarget private constructor(
    val roomId: String,
    val activityDate: String,
) {
    companion object {
        const val RoomIdKey = "roomId"
        const val ActivityDateKey = "activityDate"

        fun parse(data: Map<String, String>): RoomActivityPushTarget? {
            val rawRoomId = data[RoomIdKey] ?: return null
            if (rawRoomId.isEmpty() || rawRoomId.any { it !in '0'..'9' }) return null
            val roomId = rawRoomId.toLongOrNull()?.takeIf { it > 0L }?.toString() ?: return null

            val rawActivityDate = data[ActivityDateKey] ?: return null
            val activityDate = runCatching { MissionDate.parse(rawActivityDate) }
                .getOrNull()
                ?.takeIf { it.iso8601 == rawActivityDate }
                ?: return null

            return RoomActivityPushTarget(roomId, activityDate.iso8601)
        }
    }
}

/** A single explicit click request; each click has its own identity even for the same target. */
data class RoomActivityPushRequest(
    val requestId: String,
    val target: RoomActivityPushTarget,
)

/** Keeps the newest click until navigation acknowledges that exact request. */
class RoomActivityPushRequestState(
    pendingRequest: RoomActivityPushRequest? = null,
    lastAcceptedRequestId: String? = pendingRequest?.requestId,
) {
    var pendingRequest: RoomActivityPushRequest? = pendingRequest
        private set

    var lastAcceptedRequestId: String? = lastAcceptedRequestId
        private set

    /** Accepts a new click. A null target records an invalid or data-less click and clears older work. */
    fun accept(requestId: String, target: RoomActivityPushTarget?): Boolean {
        if (requestId.isBlank() || requestId == lastAcceptedRequestId) return false
        lastAcceptedRequestId = requestId
        pendingRequest = target?.let { RoomActivityPushRequest(requestId, it) }
        return true
    }

    /** Late acknowledgements must not consume a newer click. */
    fun acknowledge(requestId: String): Boolean {
        if (pendingRequest?.requestId != requestId) return false
        pendingRequest = null
        return true
    }

}
