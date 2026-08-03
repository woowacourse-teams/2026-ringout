package com.joon.ringout.presentation.home.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme

@Composable
fun HomeBottomBar(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .shadow(28.dp, RoundedCornerShape(32.dp), ambientColor = Color(0x26000000))
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xF2FFFFFF), RoundedCornerShape(32.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BottomBarItem(
            label = "알람",
            selected = true,
            icon = { AlarmIcon(Orange) },
            onClick = {},
            modifier = Modifier.weight(1f),
        )
        BottomBarItem(
            label = "설정",
            selected = false,
            icon = { SettingsIcon(SecondaryText) },
            onClick = onSettingsClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomBarItem(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(52.dp)
            .background(if (selected) Color(0xFFFFF0E8) else Color.Transparent, RoundedCornerShape(26.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = if (selected) Orange else SecondaryText,
        )
    }
}

@Composable
private fun AlarmIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 1.6.dp.toPx()
        val center = Offset(size.width / 2, size.height * 0.52f)
        drawCircle(color, size.minDimension * 0.28f, center, style = Stroke(stroke))
        drawLine(color, center, Offset(center.x, center.y - size.height * 0.17f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(center.x + size.width * 0.12f, center.y), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.23f, size.height * 0.2f), Offset(size.width * 0.34f, size.height * 0.12f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.77f, size.height * 0.2f), Offset(size.width * 0.66f, size.height * 0.12f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.35f, size.height * 0.83f), Offset(size.width * 0.29f, size.height * 0.91f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.65f, size.height * 0.83f), Offset(size.width * 0.71f, size.height * 0.91f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun SettingsIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val stroke = 1.5.dp.toPx()
        drawCircle(color, size.minDimension * 0.28f, center, style = Stroke(stroke))
        drawCircle(color, size.minDimension * 0.08f, center, style = Stroke(stroke))
        val inner = size.minDimension * 0.33f
        val outer = size.minDimension * 0.43f
        repeat(8) { index ->
            val angle = index * kotlin.math.PI.toFloat() / 4f
            drawLine(
                color,
                Offset(center.x + kotlin.math.cos(angle) * inner, center.y + kotlin.math.sin(angle) * inner),
                Offset(center.x + kotlin.math.cos(angle) * outer, center.y + kotlin.math.sin(angle) * outer),
                stroke,
                StrokeCap.Round,
            )
        }
    }
}

@Preview
@Composable
private fun HomeBottomBarPreview() {
    RingoutTheme {
        Column(
            modifier = Modifier
                .background(Color(0xFFF7F8F5))
                .padding(16.dp),
        ) {
            HomeBottomBar(onSettingsClick = {})
        }
    }
}
