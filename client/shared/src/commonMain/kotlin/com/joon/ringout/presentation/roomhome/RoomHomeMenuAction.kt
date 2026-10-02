package com.joon.ringout.presentation.roomhome

internal enum class RoomHomeMenuItem {
    Edit,
    Delete,
    ManageMembers,
    Leave,
}

internal enum class RoomHomeActionType {
    Delete,
    Leave,
}

internal enum class RoomHomeActionPhase {
    CheckingDeleteEligibility,
    DeleteBlockedByMembers,
    ConfirmDelete,
    RecheckingBeforeDelete,
    Deleting,
    ConfirmLeave,
    CheckingLeaveEligibility,
    Leaving,
}

internal sealed interface RoomHomeMenuActionState {
    val operationId: Long

    data class Phase(
        override val operationId: Long,
        val actionType: RoomHomeActionType,
        val value: RoomHomeActionPhase,
    ) : RoomHomeMenuActionState

    data class Error(
        override val operationId: Long,
        val actionType: RoomHomeActionType,
        val message: String,
        val isMembershipChanged: Boolean = false,
        val canRetry: Boolean = true,
    ) : RoomHomeMenuActionState

    data class Completed(
        override val operationId: Long,
        val actionType: RoomHomeActionType,
    ) : RoomHomeMenuActionState
}

internal val RoomHomeMenuActionState.blocksNavigationBack: Boolean
    get() = when (this) {
        is RoomHomeMenuActionState.Completed -> true
        is RoomHomeMenuActionState.Error -> false
        is RoomHomeMenuActionState.Phase -> value in setOf(
            RoomHomeActionPhase.RecheckingBeforeDelete,
            RoomHomeActionPhase.Deleting,
            RoomHomeActionPhase.CheckingLeaveEligibility,
            RoomHomeActionPhase.Leaving,
        )
    }

internal data class RoomHomeMenuActionCompletion(
    val operationId: Long,
    val actionType: RoomHomeActionType,
    val roomId: Long,
    val sessionIdentity: Any,
)
