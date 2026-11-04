package com.joon.ringout.data.destination

import com.joon.ringout.domain.destination.SavedDestination

internal fun SavedDestination.toEntity(): SavedDestinationEntity = SavedDestinationEntity(
    id = id,
    label = label,
    latitude = latitude,
    longitude = longitude,
)

internal fun SavedDestinationEntity.toDomain(): SavedDestination = SavedDestination(
    id = id,
    label = label,
    latitude = latitude,
    longitude = longitude,
)
