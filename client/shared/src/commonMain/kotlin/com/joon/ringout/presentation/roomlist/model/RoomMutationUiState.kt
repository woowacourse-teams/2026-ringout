package com.joon.ringout.presentation.roomlist.model

internal enum class RoomMutationType {
    Create,
    Join,
}

internal data class RoomMutationSource(
    val entryId: Long,
    val type: RoomMutationType,
    val roomId: String? = null,
)

internal data class RoomMutationUiState(
    val operationId: Long = 0L,
    val source: RoomMutationSource? = null,
    val roomId: String? = null,
    val isInProgress: Boolean = false,
    val isSuccessful: Boolean = false,
    val isMembershipConfirmed: Boolean = false,
    val successConsumed: Boolean = false,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

internal data class RoomMutationSuccess(
    val source: RoomMutationSource,
    val roomId: String,
)

internal object RoomMutationEntryIds {
    private var nextId = 0L

    fun next(): Long {
        nextId = if (nextId == Long.MAX_VALUE) 1L else nextId + 1L
        return nextId
    }
}
