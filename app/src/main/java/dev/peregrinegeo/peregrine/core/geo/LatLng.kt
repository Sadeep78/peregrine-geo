package dev.peregrinegeo.peregrine.core.geo

/**
 * Representation of geographic coordinates.
 * Pure Kotlin data structure without external framework dependencies.
 */
data class LatLng(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90, got: $latitude" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180, got: $longitude" }
    }
}
