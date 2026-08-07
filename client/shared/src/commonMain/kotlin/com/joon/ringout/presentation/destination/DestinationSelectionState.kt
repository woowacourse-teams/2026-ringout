package com.joon.ringout.presentation.destination

internal fun DestinationSelection.isConfiguredDestination(): Boolean =
    name.isNotBlank() &&
        latitude.isFinite() &&
        latitude in ValidLatitudeRange &&
        longitude.isFinite() &&
        longitude in ValidLongitudeRange

private val ValidLatitudeRange = -90.0..90.0
private val ValidLongitudeRange = -180.0..180.0
