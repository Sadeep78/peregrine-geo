package dev.peregrinegeo.peregrine.core.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin geodesic math utilities.
 * Uses standard WGS-84 spherical approximations (mean Earth radius = 6,371,000 meters).
 */
object GeoMath {

    const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Great-circle distance between two points in meters using the Haversine formula.
     */
    fun distanceMeters(from: LatLng, to: LatLng): Double {
        val lat1Rad = Math.toRadians(from.latitude)
        val lon1Rad = Math.toRadians(from.longitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val lon2Rad = Math.toRadians(to.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2.0) * sin(dLat / 2.0) +
                cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2.0) * sin(dLon / 2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))

        return EARTH_RADIUS_METERS * c
    }

    /**
     * Initial forward azimuth (bearing) from one point to another in degrees [0, 360).
     */
    fun initialBearing(from: LatLng, to: LatLng): Double {
        val lat1Rad = Math.toRadians(from.latitude)
        val lon1Rad = Math.toRadians(from.longitude)
        val lat2Rad = Math.toRadians(to.latitude)
        val lon2Rad = Math.toRadians(to.longitude)

        val dLon = lon2Rad - lon1Rad

        val y = sin(dLon) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(dLon)

        val bearingRad = atan2(y, x)
        val bearingDeg = Math.toDegrees(bearingRad)
        return (bearingDeg + 360.0) % 360.0
    }

    /**
     * Calculates destination point given starting point, bearing in degrees, and distance in meters.
     */
    fun destination(start: LatLng, bearingDeg: Double, distanceMeters: Double): LatLng {
        val dOverR = distanceMeters / EARTH_RADIUS_METERS
        val bearingRad = Math.toRadians(bearingDeg)

        val lat1Rad = Math.toRadians(start.latitude)
        val lon1Rad = Math.toRadians(start.longitude)

        val lat2Rad = kotlin.math.asin(
            sin(lat1Rad) * cos(dOverR) + cos(lat1Rad) * sin(dOverR) * cos(bearingRad)
        )
        val lon2Rad = lon1Rad + atan2(
            sin(bearingRad) * sin(dOverR) * cos(lat1Rad),
            cos(dOverR) - sin(lat1Rad) * sin(lat2Rad)
        )

        val lat2 = Math.toDegrees(lat2Rad)
        val lon2 = ((Math.toDegrees(lon2Rad) + 540.0) % 360.0) - 180.0

        return LatLng(lat2, lon2)
    }

    /**
     * Linear interpolation between two coordinates along the geodesic path (fraction in [0.0, 1.0]).
     */
    fun interpolate(from: LatLng, to: LatLng, fraction: Double): LatLng {
        val clampedFraction = fraction.coerceIn(0.0, 1.0)
        if (clampedFraction == 0.0) return from
        if (clampedFraction == 1.0) return to

        val totalDist = distanceMeters(from, to)
        if (totalDist == 0.0) return from

        val bearing = initialBearing(from, to)
        val dist = totalDist * clampedFraction
        val dest = destination(from, bearing, dist)

        // Interpolate altitude if present
        val alt = if (from.altitude != null && to.altitude != null) {
            from.altitude + (to.altitude - from.altitude) * clampedFraction
        } else from.altitude ?: to.altitude

        return LatLng(dest.latitude, dest.longitude, alt)
    }
}
