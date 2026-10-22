package dev.peregrinegeo.peregrine.core.fileio

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.geo.LatLng
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Douglas-Peucker polyline simplification algorithm.
 * Operates on geographic coordinates with tolerance in meters.
 */
object DouglasPeucker {

    const val DEFAULT_MAX_POINTS = 200_000

    fun simplify(points: List<LatLng>, toleranceMeters: Double = 2.0, maxPoints: Int = DEFAULT_MAX_POINTS): List<LatLng> {
        if (points.size <= 2) return points
        if (points.size > maxPoints) {
            // Decimate if beyond max allowable point budget
            val decimateFactor = (points.size / maxPoints) + 1
            val decimated = points.filterIndexed { index, _ -> index % decimateFactor == 0 || index == points.size - 1 }
            return simplify(decimated, toleranceMeters, maxPoints)
        }

        var maxDist = 0.0
        var maxIndex = 0

        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                maxIndex = i
            }
        }

        return if (maxDist > toleranceMeters) {
            val left = simplify(points.subList(0, maxIndex + 1), toleranceMeters, maxPoints)
            val right = simplify(points.subList(maxIndex, points.size), toleranceMeters, maxPoints)
            left.subList(0, left.size - 1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(pt: LatLng, lineStart: LatLng, lineEnd: LatLng): Double {
        val totalLineDist = GeoMath.distanceMeters(lineStart, lineEnd)
        if (totalLineDist == 0.0) return GeoMath.distanceMeters(pt, lineStart)

        val d1 = GeoMath.distanceMeters(lineStart, pt)
        val d2 = GeoMath.distanceMeters(lineEnd, pt)

        // Using triangle area via Heron's formula to find height (perpendicular distance)
        val s = (d1 + d2 + totalLineDist) / 2.0
        val areaSq = s * (s - d1) * (s - d2) * (s - totalLineDist)
        if (areaSq <= 0.0) return 0.0
        val area = sqrt(areaSq)

        return (2.0 * area) / totalLineDist
    }
}
