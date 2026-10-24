package com.joon.ringout.presentation.developertools.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joon.ringout.domain.push.PushTokenRegistrationStatus

@Composable
internal fun PushTokenRegistrationSection(
    status: PushTokenRegistrationStatus,
    modifier: Modifier = Modifier,
) {
    val (label, description) = when (status) {
        PushTokenRegistrationStatus.WaitingForAuthentication ->
            "로그인 대기 중" to "로그인하면 FCM 토큰 등록을 시도합니다."
        PushTokenRegistrationStatus.WaitingForToken ->
            "FCM 토큰 대기 중" to "기기에서 FCM 토큰을 가져오고 있습니다."
        PushTokenRegistrationStatus.Ready ->
            "등록 대기 중" to "FCM 토큰을 서버에 등록할 준비가 되었습니다."
        PushTokenRegistrationStatus.Registering ->
            "등록 중" to "FCM 토큰을 서버에 등록하고 있습니다."
        PushTokenRegistrationStatus.Succeeded ->
            "등록 성공" to "서버가 FCM 토큰 등록을 완료했습니다."
        PushTokenRegistrationStatus.Failed ->
            "등록 실패" to "서버 등록 요청에 실패했습니다. 앱 로그를 확인하세요."
    }
    val statusColor = when (status) {
        PushTokenRegistrationStatus.Succeeded -> MaterialTheme.colorScheme.primary
        PushTokenRegistrationStatus.Failed -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "FCM 토큰 등록 결과",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = statusColor)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
    }
}
