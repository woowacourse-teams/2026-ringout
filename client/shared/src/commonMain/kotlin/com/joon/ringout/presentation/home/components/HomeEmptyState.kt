package com.joon.ringout.presentation.home.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joon.ringout.LocalRingoutThemeMode
import com.joon.ringout.ThemeMode
import com.joon.ringout.ringoutColors
import org.jetbrains.compose.resources.painterResource
import ringout.shared.generated.resources.Res
import ringout.shared.generated.resources.home_empty_logo

@Composable
internal fun HomeEmptyState(
    onAddAlarm: () -> Unit,
    onMyPageClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = LocalRingoutThemeMode.current == ThemeMode.Dark

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
        ) {
            EmptyHomeHeader(
                onMyPageClick = onMyPageClick,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                EmptyAlarmPrompt(
                    onAddAlarm = onAddAlarm,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(if (isDarkTheme) Color.Black else LightNavigationDivider),
        )
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun EmptyHomeHeader(
    onMyPageClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(65.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "알람",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 28.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.Black,
            ),
        )

        Spacer(Modifier.weight(1f))

        HomeMyPageButton(onClick = onMyPageClick)
    }
}

@Composable
private fun EmptyAlarmPrompt(
    onAddAlarm: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(MaterialTheme.ringoutColors.profileIconLoggedInBackground),
        ) {
            Image(
                painter = painterResource(Res.drawable.home_empty_logo),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .width(313.dp)
                .height(122.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .width(174.dp)
                    .height(65.dp)
                    .clickable(role = Role.Button, onClick = onAddAlarm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    text = "생성된 알람이 없습니다.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 16.sp,
                        lineHeight = 19.2.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "알람 생성하기",
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 21.6.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

private val LightNavigationDivider = Color(0xFFE5E7EB)
