package com.joon.ringout.presentation.destination

import com.google.android.libraries.places.api.model.Place
import kotlin.math.roundToInt

internal fun List<Place>.toDestinationSelections(): List<DestinationSelection> =
    mapNotNull { place ->
        val location = place.location ?: return@mapNotNull null
        val name = place.displayName.orEmpty()
            .ifBlank { place.formattedAddress.orEmpty() }
        val address = place.formattedAddress.orEmpty()
            .ifBlank { name }
        if (name.isBlank() || address.isBlank()) return@mapNotNull null

        DestinationSelection(
            name = name,
            address = address,
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }.distinctBy { selection ->
        val latitude = (selection.latitude * CoordinateDeduplicationScale).roundToInt()
        val longitude = (selection.longitude * CoordinateDeduplicationScale).roundToInt()
        latitude to longitude
    }

private const val CoordinateDeduplicationScale = 100_000
