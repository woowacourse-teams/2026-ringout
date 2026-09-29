package com.joon.ringout.presentation.mypage.component

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
import com.joon.ringout.presentation.mypage.model.MyPageDataAction

@Composable
fun MyPageDataActionDialog(
    action: MyPageDataAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConfirmationDialog(
        title = when (action) {
            MyPageDataAction.Save -> "데이터 저장하기"
            MyPageDataAction.Load -> "데이터 불러오기"
        },
        description = when (action) {
            MyPageDataAction.Save -> "지금까지 저장된 알람 기록을 서버에 저장할까요?"
            MyPageDataAction.Load -> "서버에 저장된 알람 기록을 불러올까요?"
        },
        confirmLabel = when (action) {
            MyPageDataAction.Save -> "저장"
            MyPageDataAction.Load -> "불러오기"
        },
        confirmColor = MaterialTheme.colorScheme.primary,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        modifier = modifier,
        layout = DataDialogLayout,
    )
}

private val DataDialogLayout = ConfirmationDialogLayout(
    maxWidth = 376.dp,
    screenPadding = 12.dp,
    actionRowMaxWidth = 290.dp,
    cancelButtonWeight = 118f,
    actionAlignment = Alignment.CenterHorizontally,
    titleMaxLines = Int.MAX_VALUE,
    descriptionMaxLines = Int.MAX_VALUE,
)

@Preview(name = "Save data", widthDp = 402, heightDp = 400)
@Composable
private fun MyPageDataActionDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageDataActionDialog(
            action = MyPageDataAction.Save,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Load data - Light", widthDp = 402, heightDp = 400)
@Composable
private fun MyPageDataLoadDialogPreview() {
    RingoutTheme(ThemeMode.Light) {
        MyPageDataActionDialog(
            action = MyPageDataAction.Load,
            onDismiss = {},
            onConfirm = {},
        )
    }
}

@Preview(name = "Save data - Small screen", widthDp = 320, heightDp = 480, fontScale = 1.3f)
@Composable
private fun MyPageDataSmallDialogPreview() {
    RingoutTheme(ThemeMode.Dark) {
        MyPageDataActionDialog(
            action = MyPageDataAction.Save,
            onDismiss = {},
            onConfirm = {},
        )
    }
}
