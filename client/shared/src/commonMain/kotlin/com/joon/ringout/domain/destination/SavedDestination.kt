package com.joon.ringout.domain.destination

data class SavedDestination(
    val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(id >= 0L) { "Saved destination id must not be negative: $id" }
        require(name.isNotBlank()) { "Saved destination name must not be blank." }
        require(latitude.isFinite() && latitude in ValidLatitudeRange) {
            "Saved destination latitude is out of range: $latitude"
        }
        require(longitude.isFinite() && longitude in ValidLongitudeRange) {
            "Saved destination longitude is out of range: $longitude"
        }
    }
}

private val ValidLatitudeRange = -90.0..90.0
private val ValidLongitudeRange = -180.0..180.0
