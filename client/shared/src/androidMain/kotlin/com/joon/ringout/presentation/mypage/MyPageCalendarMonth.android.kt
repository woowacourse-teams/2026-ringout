package com.joon.ringout.presentation.mypage

import com.joon.ringout.domain.missionhistory.MissionYearMonth
import java.time.LocalDate

internal actual fun currentMissionYearMonth(): MissionYearMonth =
    LocalDate.now().let { MissionYearMonth(it.year, it.monthValue) }
