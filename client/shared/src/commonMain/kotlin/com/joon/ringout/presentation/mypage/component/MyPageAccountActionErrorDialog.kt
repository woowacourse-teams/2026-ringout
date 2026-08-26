package com.joon.ringout.presentation.mypage.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.presentation.mypage.model.MyPageAccountAction

@Composable
fun MyPageAccountActionErrorDialog(
    action: MyPageAccountAction,
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(action.errorTitle) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
            }
        },
    )
}

private val MyPageAccountAction.errorTitle: String
    get() = when (this) {
        MyPageAccountAction.Logout -> "로그아웃을 완료하지 못했습니다"
        MyPageAccountAction.Withdraw -> "회원 탈퇴를 완료하지 못했습니다"
    }

@Preview(name = "My Page logout error")
@Composable
private fun MyPageLogoutErrorDialogPreview() {
    RingoutTheme(ThemeMode.Light) {
        MyPageAccountActionErrorDialog(
            action = MyPageAccountAction.Logout,
            message = "로그아웃을 다시 시도해 주세요.",
            onDismiss = {},
        )
    }
}

@Preview(name = "My Page withdrawal error")
@Composable
private fun MyPageWithdrawalErrorDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageAccountActionErrorDialog(
            action = MyPageAccountAction.Withdraw,
            message = "회원 탈퇴를 다시 시도해 주세요.",
            onDismiss = {},
        )
    }
}
