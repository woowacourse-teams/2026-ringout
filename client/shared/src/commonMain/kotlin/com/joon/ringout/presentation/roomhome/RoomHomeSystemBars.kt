package com.joon.ringout.presentation.roomhome

import androidx.compose.runtime.Composable

/** 이미지 위에서는 밝은 아이콘을, 밝은 본문 위에서는 어두운 아이콘을 사용한다. */
@Composable
internal expect fun RoomHomeSystemBars(useDarkStatusIcons: Boolean, useDarkNavigationIcons: Boolean)
