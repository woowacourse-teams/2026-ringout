package com.joon.ringout.presentation.destination

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.joon.ringout.data.database.getRingoutDatabase
import com.joon.ringout.data.destination.DefaultDestinationRepository
import com.joon.ringout.data.destination.RoomDestinationDataSource
import com.joon.ringout.domain.destination.DestinationRepository

@Composable
internal actual fun rememberDestinationRepository(): DestinationRepository = remember {
    DefaultDestinationRepository(
        RoomDestinationDataSource(getRingoutDatabase().destinationDao()),
    )
}
