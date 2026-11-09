package com.joon.ringout.presentation.arrival.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.RingoutTheme
import com.joon.ringout.domain.arrival.ArrivalGrowth
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.arrival_background
import ringout.shared.generated.resources.arrival_tree

@Composable
internal fun ArrivalCelebration(
    growth: ArrivalGrowth,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.TopCenter) {
        // Keep the original Figma backdrop in its content slot, behind the scrollable result.
        Image(
            painter = painterResource(Res.drawable.arrival_background),
            contentDescription = null,
            modifier = Modifier
                .offset(y = (-73).dp)
                .wrapContentSize(Alignment.TopCenter, unbounded = true)
                .size(783.dp),
            contentScale = ContentScale.Fit,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${growth.successfulDayCount}번째",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "목적지에 도착했어요!",
                    modifier = Modifier.semantics { heading() },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 32.sp,
                        lineHeight = 38.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
            if (growth.isTreeComplete) {
                Image(
                    painter = painterResource(Res.drawable.arrival_tree),
                    contentDescription = null,
                    modifier = Modifier.size(width = 117.dp, height = 186.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                ArrivalSeedAnimation(modifier = Modifier.size(186.dp))
            }
            Text(
                text = buildAnnotatedString {
                    if (growth.isTreeComplete) {
                        append("씨앗이 모두 자라\n나무가 되었어요!")
                    } else {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append("${growth.remainingDays}일")
                        }
                        append(" 더 도착하면\n나무로 자라요")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = 28.sp,
                    lineHeight = 40.sp,
                    letterSpacing = (-0.28).sp,
                    fontWeight = FontWeight.Bold,
                ),
                textAlign = TextAlign.Center,
            )
            ArrivalGrowthProgress(growth = growth)
        }
    }
}

@Preview(widthDp = 402, heightDp = 702)
@Composable
private fun ArrivalCelebrationPreview() {
    RingoutTheme { ArrivalCelebration(growth = ArrivalGrowth(4), modifier = Modifier.fillMaxSize()) }
}

@Preview(widthDp = 402, heightDp = 702)
@Composable
private fun ArrivalTreeCelebrationPreview() {
    RingoutTheme { ArrivalCelebration(growth = ArrivalGrowth(5), modifier = Modifier.fillMaxSize()) }
}
