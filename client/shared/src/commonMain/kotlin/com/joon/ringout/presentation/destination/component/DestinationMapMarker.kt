package com.joon.ringout.presentation.destination.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme

private val AddressBubbleHeight = 42.dp
private val MarkerWidth = 54.dp
private val MarkerHeight = 80.dp
private val AddressPinSpacing = 6.dp
private val MarkerTipBottomInset = MarkerHeight * .14f

@Composable
internal fun DestinationMapMarker(
    address: String,
    modifier: Modifier = Modifier,
) {
    val colors = DestinationManagementPalette
    val shape = RoundedCornerShape(15.dp)
    Column(
        modifier = modifier
            // 부모 중앙에 배치했을 때 핀의 끝이 지도 중심 좌표를 가리키도록 한다.
            .offset(y = -(AddressBubbleHeight + AddressPinSpacing + MarkerHeight) / 2 + MarkerTipBottomInset)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AddressPinSpacing),
    ) {
        Box(
            modifier = Modifier
                .width(299.dp)
                .height(AddressBubbleHeight)
                .dropShadow(
                    shape,
                    Shadow(radius = 4.dp, color = colors.shadow, offset = DpOffset(0.dp, 4.dp)),
                )
                .background(colors.surface, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = address.removePrefix("대한민국 ").trimStart(),
                modifier = Modifier.fillMaxWidth(),
                color = colors.title,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        Canvas(Modifier.size(MarkerWidth, MarkerHeight)) {
            drawOval(
                color = colors.pinShadow,
                topLeft = Offset(size.width * .17f, size.height * .84f),
                size = Size(size.width * .66f, size.height * .12f),
            )
            val pin = Path().apply {
                moveTo(size.width / 2f, size.height * .86f)
                cubicTo(
                    size.width * .42f,
                    size.height * .72f,
                    size.width * .12f,
                    size.height * .52f,
                    size.width * .12f,
                    size.height * .32f,
                )
                cubicTo(
                    size.width * .12f,
                    size.height * .12f,
                    size.width * .29f,
                    size.height * .04f,
                    size.width / 2f,
                    size.height * .04f,
                )
                cubicTo(
                    size.width * .71f,
                    size.height * .04f,
                    size.width * .88f,
                    size.height * .12f,
                    size.width * .88f,
                    size.height * .32f,
                )
                cubicTo(
                    size.width * .88f,
                    size.height * .52f,
                    size.width * .58f,
                    size.height * .72f,
                    size.width / 2f,
                    size.height * .86f,
                )
                close()
            }
            drawPath(pin, colors.pinFill)
            drawPath(pin, colors.surface, style = Stroke(width = 3.dp.toPx()))
            drawCircle(
                color = colors.surface,
                radius = size.width * .16f,
                center = Offset(size.width / 2f, size.height * .3f),
            )
        }
    }
}

@Preview
@Composable
private fun DestinationMapMarkerPreview() {
    RingoutTheme {
        Box(Modifier.size(402.dp, 300.dp), contentAlignment = Alignment.Center) {
            DestinationMapMarker("경기도 수원시 팔달구 지동 370-41")
        }
    }
}
