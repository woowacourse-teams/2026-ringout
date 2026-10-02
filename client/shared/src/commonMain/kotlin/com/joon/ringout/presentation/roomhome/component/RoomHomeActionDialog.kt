package com.joon.ringout.presentation.roomhome.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.common.component.ConfirmationDialog
import com.joon.ringout.presentation.common.component.ConfirmationDialogLayout
import com.joon.ringout.presentation.roomhome.RoomHomeActionPhase
import com.joon.ringout.presentation.roomhome.RoomHomeActionType
import com.joon.ringout.presentation.roomhome.RoomHomeMenuActionState
import com.joon.ringout.presentation.roomhome.roomHomeColors
import com.joon.ringout.ringoutColors

@Composable
internal fun RoomHomeActionDialog(
    state: RoomHomeMenuActionState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onManageMembers: () -> Unit,
    onGoHome: (Long) -> Unit,
) {
    val content = actionDialogContent(state)
    val isProtected = state.isProtectedAction()
    val isBusy = content.isBusy
    val colors = roomHomeColors()
    val primaryAction = when (content.primaryAction) {
        ActionDialogPrimary.Confirm -> onConfirm
        ActionDialogPrimary.Retry -> onRetry
        ActionDialogPrimary.ManageMembers -> onManageMembers
        ActionDialogPrimary.Home -> ({ onGoHome(state.operationId) })
        ActionDialogPrimary.Dismiss -> onCancel
        ActionDialogPrimary.None -> null
    }
    val cancellablePrecheck = isBusy && !isProtected

    ConfirmationDialog(
        title = content.title,
        description = content.description,
        confirmLabel = if (cancellablePrecheck) "취소" else content.primaryLabel,
        confirmColor = if (cancellablePrecheck) {
            MaterialTheme.ringoutColors.dialog.cancel
        } else {
            colors.actionDialogPrimary
        },
        cancelLabel = if (isBusy) null else content.secondaryLabel,
        onDismiss = { if (!isProtected) onCancel() },
        onConfirm = if (cancellablePrecheck) onCancel else primaryAction ?: {},
        layout = ConfirmationDialogLayout(
            titleMaxLines = Int.MAX_VALUE,
            descriptionMaxLines = Int.MAX_VALUE,
            actionAlignment = if (content.secondaryLabel == null) {
                Alignment.CenterHorizontally
            } else {
                Alignment.End
            },
            singleActionMaxWidth = 162.dp,
        ),
        cancelOnClick = onCancel,
        showConfirmButton = !isBusy || cancellablePrecheck,
        dismissOnBackPress = !isProtected,
        dismissOnClickOutside = !isProtected,
        content = if (isBusy) {
            {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = colors.actionDialogPrimary,
                        strokeWidth = 3.dp,
                    )
                }
            }
        } else {
            null
        },
    )
}

private data class ActionDialogContent(
    val title: String,
    val description: String,
    val primaryLabel: String,
    val secondaryLabel: String? = null,
    val primaryAction: ActionDialogPrimary,
    val isBusy: Boolean = false,
)

private enum class ActionDialogPrimary { Confirm, Retry, ManageMembers, Home, Dismiss, None }

private fun actionDialogContent(state: RoomHomeMenuActionState): ActionDialogContent = when (state) {
    is RoomHomeMenuActionState.Completed -> when (state.actionType) {
        RoomHomeActionType.Delete -> ActionDialogContent(
            title = "모임 삭제",
            description = "모임 삭제가 완료되었습니다.",
            primaryLabel = "홈으로 이동",
            primaryAction = ActionDialogPrimary.Home,
        )
        RoomHomeActionType.Leave -> ActionDialogContent(
            title = "모임 탈퇴",
            description = "모임 탈퇴가 완료되었습니다.",
            primaryLabel = "홈으로 이동",
            primaryAction = ActionDialogPrimary.Dismiss,
        )
    }
    is RoomHomeMenuActionState.Error -> when {
        state.isMembershipChanged -> ActionDialogContent(
            title = actionTitle(state.actionType),
            description = state.message,
            primaryLabel = "홈으로 이동",
            secondaryLabel = "확인",
            primaryAction = ActionDialogPrimary.Home,
        )
        state.canRetry -> ActionDialogContent(
            title = actionTitle(state.actionType),
            description = state.message,
            primaryLabel = "다시 시도",
            secondaryLabel = "취소",
            primaryAction = ActionDialogPrimary.Retry,
        )
        else -> ActionDialogContent(
            title = actionTitle(state.actionType),
            description = state.message,
            primaryLabel = "확인",
            primaryAction = ActionDialogPrimary.Dismiss,
        )
    }
    is RoomHomeMenuActionState.Phase -> when (state.value) {
        RoomHomeActionPhase.CheckingDeleteEligibility -> ActionDialogContent(
            title = "모임 삭제",
            description = "회원 정보를 확인하고 있어요.",
            primaryLabel = "취소",
            primaryAction = ActionDialogPrimary.None,
            isBusy = true,
        )
        RoomHomeActionPhase.DeleteBlockedByMembers -> ActionDialogContent(
            title = "모임 삭제",
            description = "모임을 삭제하려면 가입한 회원이 없어야 합니다.\n회원 관리 탭을 통하여 회원을 모두 추방하고 다시 진행해주세요.",
            primaryLabel = "회원 관리로 이동",
            secondaryLabel = "취소",
            primaryAction = ActionDialogPrimary.ManageMembers,
        )
        RoomHomeActionPhase.ConfirmDelete -> ActionDialogContent(
            title = "모임 삭제",
            description = "모임을 삭제하면 모임의 모든 데이터가 삭제되며 복구할 수 없습니다.\n정말 삭제할까요?",
            primaryLabel = "모임 삭제",
            secondaryLabel = "취소",
            primaryAction = ActionDialogPrimary.Confirm,
        )
        RoomHomeActionPhase.RecheckingBeforeDelete -> ActionDialogContent(
            title = "모임 삭제",
            description = "삭제할 수 있는 상태인지 다시 확인하고 있어요.",
            primaryLabel = "모임 삭제",
            primaryAction = ActionDialogPrimary.None,
            isBusy = true,
        )
        RoomHomeActionPhase.Deleting -> ActionDialogContent(
            title = "모임 삭제",
            description = "모임을 삭제하고 있어요.",
            primaryLabel = "모임 삭제",
            primaryAction = ActionDialogPrimary.None,
            isBusy = true,
        )
        RoomHomeActionPhase.ConfirmLeave -> ActionDialogContent(
            title = "모임 탈퇴",
            description = "정말 모임에서 탈퇴할까요?\n모임에서 활동한 모든 데이터가 삭제되며, 다시 복구할 수 없습니다.",
            primaryLabel = "모임 탈퇴",
            secondaryLabel = "취소",
            primaryAction = ActionDialogPrimary.Confirm,
        )
        RoomHomeActionPhase.CheckingLeaveEligibility -> ActionDialogContent(
            title = "모임 탈퇴",
            description = "참여 상태를 확인하고 있어요.",
            primaryLabel = "모임 탈퇴",
            primaryAction = ActionDialogPrimary.None,
            isBusy = true,
        )
        RoomHomeActionPhase.Leaving -> ActionDialogContent(
            title = "모임 탈퇴",
            description = "모임에서 탈퇴하고 있어요.",
            primaryLabel = "모임 탈퇴",
            primaryAction = ActionDialogPrimary.None,
            isBusy = true,
        )
    }
}

private fun actionTitle(actionType: RoomHomeActionType): String = when (actionType) {
    RoomHomeActionType.Delete -> "모임 삭제"
    RoomHomeActionType.Leave -> "모임 탈퇴"
}

private fun RoomHomeMenuActionState.isProtectedAction(): Boolean = when (this) {
    is RoomHomeMenuActionState.Completed -> true
    is RoomHomeMenuActionState.Error -> false
    is RoomHomeMenuActionState.Phase -> value in setOf(
        RoomHomeActionPhase.RecheckingBeforeDelete,
        RoomHomeActionPhase.Deleting,
        RoomHomeActionPhase.CheckingLeaveEligibility,
        RoomHomeActionPhase.Leaving,
    )
}

@Preview(name = "모임 삭제 확인", widthDp = 360)
@Composable
private fun RoomHomeDeleteDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeActionDialog(
            state = RoomHomeMenuActionState.Phase(1, RoomHomeActionType.Delete, RoomHomeActionPhase.ConfirmDelete),
            onCancel = {}, onConfirm = {}, onRetry = {}, onManageMembers = {}, onGoHome = {},
        )
    }
}

@Preview(name = "모임 삭제 완료", widthDp = 360)
@Composable
private fun RoomHomeDeleteCompleteDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeActionDialog(
            state = RoomHomeMenuActionState.Completed(1, RoomHomeActionType.Delete),
            onCancel = {}, onConfirm = {}, onRetry = {}, onManageMembers = {}, onGoHome = {},
        )
    }
}

@Preview(name = "다른 회원이 남은 모임 삭제 안내", widthDp = 360)
@Composable
private fun RoomHomeDeleteBlockedDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeActionDialog(
            state = RoomHomeMenuActionState.Phase(
                2,
                RoomHomeActionType.Delete,
                RoomHomeActionPhase.DeleteBlockedByMembers,
            ),
            onCancel = {}, onConfirm = {}, onRetry = {}, onManageMembers = {}, onGoHome = {},
        )
    }
}

@Preview(name = "모임 탈퇴 확인 · 라이트", widthDp = 360)
@Composable
private fun RoomHomeLeaveDialogPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeActionDialog(
            state = RoomHomeMenuActionState.Phase(3, RoomHomeActionType.Leave, RoomHomeActionPhase.ConfirmLeave),
            onCancel = {}, onConfirm = {}, onRetry = {}, onManageMembers = {}, onGoHome = {},
        )
    }
}

@Preview(name = "모임 삭제 진행 중", widthDp = 360)
@Composable
private fun RoomHomeDeleteProgressDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeActionDialog(
            state = RoomHomeMenuActionState.Phase(4, RoomHomeActionType.Delete, RoomHomeActionPhase.Deleting),
            onCancel = {}, onConfirm = {}, onRetry = {}, onManageMembers = {}, onGoHome = {},
        )
    }
}

@Preview(name = "모임 동작 오류", widthDp = 360)
@Composable
private fun RoomHomeActionErrorDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        RoomHomeActionDialog(
            state = RoomHomeMenuActionState.Error(
                operationId = 5,
                actionType = RoomHomeActionType.Leave,
                message = "모임에서 탈퇴하지 못했어요. 상태를 확인하고 다시 시도해 주세요.",
            ),
            onCancel = {}, onConfirm = {}, onRetry = {}, onManageMembers = {}, onGoHome = {},
        )
    }
}

@Preview(name = "좁은 화면 · 큰 글씨", widthDp = 320, heightDp = 640, fontScale = 1.5f)
@Composable
private fun RoomHomeActionDialogCompactPreview() {
    RingoutTheme(ThemeMode.Light) {
        RoomHomeActionDialog(
            state = RoomHomeMenuActionState.Phase(
                6,
                RoomHomeActionType.Delete,
                RoomHomeActionPhase.ConfirmDelete,
            ),
            onCancel = {}, onConfirm = {}, onRetry = {}, onManageMembers = {}, onGoHome = {},
        )
    }
}
