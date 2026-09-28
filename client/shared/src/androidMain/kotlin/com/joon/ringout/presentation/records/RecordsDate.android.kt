package com.joon.ringout.presentation.records

import com.joon.ringout.domain.missionhistory.MissionDate
import java.time.LocalDate

internal actual fun currentRecordsDate(): MissionDate = MissionDate.parse(LocalDate.now().toString())
