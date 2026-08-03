package com.joon.ringout.presentation.activemission.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ActiveAlarmTrackingHeader(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "알람 목록으로 돌아가기",
                    onClick = onBackClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            TrackingBackIcon()
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "목적지로 이동 중",
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text(
                text = "현재 위치와 목적지를 확인하세요",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            )
        }
    }
}

@Composable
internal fun ActiveAlarmTrackingCard(
    destinationName: String,
    limitMinutes: Int,
    countdown: String,
    hasCurrentLocation: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shape)
            .semantics(mergeDescendants = true) {
                contentDescription =
                    "남은 제한시간 $countdown, 목적지 $destinationName, " +
                    if (hasCurrentLocation) {
                        "현재 위치 지도에 표시됨"
                    } else {
                        "현재 위치 확인 중"
                    }
            }
            .padding(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "남은 제한시간",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = countdown,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 32.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
            Text(
                text = "총 ${limitMinutes}분",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }

        Spacer(Modifier.height(20.dp))

        TrackingLocationRow(
            label = "목적지",
            value = destinationName,
            marker = TrackingMarker.Destination,
        )

        Spacer(Modifier.height(14.dp))

        TrackingLocationRow(
            label = "현재 위치",
            value = if (hasCurrentLocation) {
                "지도에 표시됨"
            } else {
                "위치를 확인하고 있어요"
            },
            marker = TrackingMarker.Current,
        )
    }
}

@Composable
internal fun ActiveAlarmMapUnavailable(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "지도를 불러오지 못했습니다",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TrackingLocationRow(
    label: String,
    value: String,
    marker: TrackingMarker,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrackingLocationMarker(marker)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Text(
                text = value,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                ),
            )
        }
    }
}

@Composable
private fun TrackingLocationMarker(marker: TrackingMarker) {
    val primary = MaterialTheme.colorScheme.primary
    val currentFill = MaterialTheme.colorScheme.onSurface
    val currentBorder = MaterialTheme.colorScheme.surface

    Canvas(
        modifier = Modifier
            .size(22.dp)
            .semantics {
                contentDescription = when (marker) {
                    TrackingMarker.Destination -> "목적지 표시"
                    TrackingMarker.Current -> "현재 위치 표시"
                }
            },
    ) {
        when (marker) {
            TrackingMarker.Destination -> {
                drawCircle(
                    color = primary,
                    radius = size.minDimension * 0.42f,
                )
                drawCircle(
                    color = Color.White,
                    radius = size.minDimension * 0.18f,
                )
            }

            TrackingMarker.Current -> {
                drawCircle(
                    color = currentBorder,
                    radius = size.minDimension * 0.46f,
                )
                drawCircle(
                    color = currentFill,
                    radius = size.minDimension * 0.31f,
                )
            }
        }
    }
}

@Composable
private fun TrackingBackIcon() {
    val color = MaterialTheme.colorScheme.onSurface

    Canvas(Modifier.size(22.dp)) {
        val strokeWidth = 1.8.dp.toPx()
        drawLine(
            color = color,
            start = Offset(size.width * 0.65f, size.height * 0.2f),
            end = Offset(size.width * 0.35f, size.height * 0.5f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.35f, size.height * 0.5f),
            end = Offset(size.width * 0.65f, size.height * 0.8f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

private enum class TrackingMarker {
    Destination,
    Current,
}
