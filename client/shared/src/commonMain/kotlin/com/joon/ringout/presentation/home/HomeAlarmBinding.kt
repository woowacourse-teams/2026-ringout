package com.joon.ringout.presentation.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.joon.ringout.alarm.AlarmController

@Composable
internal fun HomeAlarmBinding(
    alarmController: AlarmController,
    homeViewModel: HomeViewModel,
) {
    LaunchedEffect(alarmController, homeViewModel) {
        homeViewModel.observeAlarms(alarmController.savedAlarms)
    }
}
