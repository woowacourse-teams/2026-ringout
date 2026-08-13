package com.joon.ringout.presentation.activemission.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.joon.ringout.RingoutTheme
import com.joon.ringout.ThemeMode
import com.joon.ringout.alarm.MissionLocationPermissionDecision

@Composable
internal fun MissionLocationPermissionDialog(
    decision: MissionLocationPermissionDecision?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val content = decision?.dialogContent ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(content.title) },
        text = { Text(content.message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(content.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
    )
}

private data class MissionLocationPermissionDialogContent(
    val title: String,
    val message: String,
    val confirmLabel: String,
)

private val MissionLocationPermissionDecision.dialogContent:
    MissionLocationPermissionDialogContent?
    get() = when (this) {
        MissionLocationPermissionDecision.EXPLAIN_WHEN_IN_USE ->
            MissionLocationPermissionDialogContent(
                title = "목적지 도착을 확인할게요",
                message =
                    "알람을 해제한 뒤 목적지까지 이동하는 동안 현재 위치가 필요해요. " +
                        "먼저 앱을 사용하는 동안의 위치 접근을 허용해 주세요.",
                confirmLabel = "계속",
            )

        MissionLocationPermissionDecision.EXPLAIN_ALWAYS ->
            MissionLocationPermissionDialogContent(
                title = "화면이 꺼져도 확인할게요",
                message =
                    "알람 미션은 앱이 백그라운드에 있어도 도착 여부를 확인해야 해요. " +
                        "다음 화면에서 위치 접근을 ‘항상’으로 허용해 주세요.",
                confirmLabel = "항상 허용하기",
            )

        MissionLocationPermissionDecision.CONFIRM_ALWAYS_RESULT ->
            MissionLocationPermissionDialogContent(
                title = "위치 권한 선택을 확인할게요",
                message =
                    "시스템 권한 화면에서 선택을 마쳤다면 확인을 눌러 주세요. " +
                        "‘앱을 사용하는 동안’으로 유지한 경우 제한 모드로 계속 진행합니다.",
                confirmLabel = "선택 완료",
            )

        MissionLocationPermissionDecision.WARN_REDUCED_ACCURACY ->
            MissionLocationPermissionDialogContent(
                title = "정확한 위치가 필요해요",
                message =
                    "대략적인 위치만 사용하면 목적지 도착을 정확히 판정하기 어려워요. " +
                        "이번 미션에서 정확한 위치 사용을 허용해 주세요.",
                confirmLabel = "정확한 위치 허용",
            )

        MissionLocationPermissionDecision.READY,
        MissionLocationPermissionDecision.SERVICES_DISABLED,
        MissionLocationPermissionDecision.DENIED,
        MissionLocationPermissionDecision.ALWAYS_REQUEST_FAILED,
        MissionLocationPermissionDecision.RESTRICTED,
        -> null
    }

@Preview(showBackground = true)
@Composable
private fun MissionLocationPermissionDialogPreview() {
    RingoutTheme(themeMode = ThemeMode.Light) {
        MissionLocationPermissionDialog(
            decision = MissionLocationPermissionDecision.EXPLAIN_ALWAYS,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
