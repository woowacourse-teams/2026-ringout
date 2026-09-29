package com.joon.ringout.presentation.roomlist.roomdetail

import com.joon.ringout.domain.auth.AuthSessionState
import com.joon.ringout.presentation.roomlist.model.RoomUiModel

internal enum class RoomJoinAction {
    Disabled,
    RequestLogin,
    Join,
}

internal fun roomJoinAction(
    room: RoomUiModel,
    authSessionState: AuthSessionState,
): RoomJoinAction = when {
    room.isJoined -> RoomJoinAction.Disabled
    authSessionState == AuthSessionState.Authenticated -> RoomJoinAction.Join
    authSessionState == AuthSessionState.Unauthenticated -> RoomJoinAction.RequestLogin
    else -> RoomJoinAction.Disabled
}
