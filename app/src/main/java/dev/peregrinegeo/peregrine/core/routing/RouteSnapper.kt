package dev.peregrinegeo.peregrine.core.routing

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.geo.LatLng

enum class SnapMode {
    Direct,
    Walking,
    Cycling,
    Driving
}

data class RouteLeg(
    val from: LatLng,
    val to: LatLng,
    val snappedPoints: List<LatLng>,
    val distanceMeters: Double,
    val isFallback: Boolean
)

data class RoutePlanResult(
    val legs: List<RouteLeg>,
    val fullPath: List<LatLng>,
    val totalDistanceMeters: Double,
    val hasFallbackLegs: Boolean
)

data class RouteLegResult(
    val points: List<LatLng>,
    val distanceMeters: Double
)

interface RoutingBackend {
    fun fetchRoute(from: LatLng, to: LatLng, mode: SnapMode): RouteLegResult?
}

/**
 * RouteSnapper manages multi-waypoint polyline snapping.
 * Re-snaps only affected legs when a waypoint is edited and falls back to direct lines if a leg cannot be routed.
 */
class RouteSnapper(
    private val backend: RoutingBackend? = null
) {
    private val legCache = mutableMapOf<String, RouteLeg>()

    fun planRoute(waypoints: List<LatLng>, mode: SnapMode): RoutePlanResult {
        if (waypoints.isEmpty()) {
            return RoutePlanResult(emptyList(), emptyList(), 0.0, false)
        }
        if (waypoints.size == 1) {
            return RoutePlanResult(emptyList(), waypoints, 0.0, false)
        }

        val legs = mutableListOf<RouteLeg>()
        var totalDist = 0.0
        var hasFallback = false
        val allPoints = mutableListOf<LatLng>()

        for (i in 0 until waypoints.size - 1) {
            val from = waypoints[i]
            val to = waypoints[i + 1]
            val leg = snapLeg(from, to, mode)
            legs.add(leg)
            totalDist += leg.distanceMeters
            if (leg.isFallback) hasFallback = true

            if (i == 0) {
                allPoints.addAll(leg.snappedPoints)
            } else {
                // Avoid duplicating junction waypoint
                if (leg.snappedPoints.size > 1) {
                    allPoints.addAll(leg.snappedPoints.subList(1, leg.snappedPoints.size))
                }
            }
        }

        return RoutePlanResult(
            legs = legs,
            fullPath = allPoints,
            totalDistanceMeters = totalDist,
            hasFallbackLegs = hasFallback
        )
    }

    /**
     * Efficiently updates route when single waypoint at [index] is modified.
     * Re-snaps only affected legs (index - 1) and (index).
     */
    fun updateWaypoint(
        waypoints: List<LatLng>,
        index: Int,
        newPosition: LatLng,
        mode: SnapMode
    ): RoutePlanResult {
        val updated = waypoints.toMutableList()
        updated[index] = newPosition
        return planRoute(updated, mode)
    }

    private fun snapLeg(from: LatLng, to: LatLng, mode: SnapMode): RouteLeg {
        val cacheKey = "${from.latitude},${from.longitude}->${to.latitude},${to.longitude}:$mode"
        val cached = legCache[cacheKey]
        if (cached != null) return cached

        if (mode == SnapMode.Direct || backend == null) {
            val directLeg = createDirectLeg(from, to)
            legCache[cacheKey] = directLeg
            return directLeg
        }

        try {
            val routed = backend.fetchRoute(from, to, mode)
            if (routed != null && routed.points.isNotEmpty()) {
                val leg = RouteLeg(
                    from = from,
                    to = to,
                    snappedPoints = routed.points,
                    distanceMeters = routed.distanceMeters,
                    isFallback = false
                )
                legCache[cacheKey] = leg
                return leg
            }
        } catch (_: Exception) {
            // Fall back to direct line on route failure
        }

        // Direct fallback on route failure
        val fallbackLeg = RouteLeg(
            from = from,
            to = to,
            snappedPoints = listOf(from, to),
            distanceMeters = GeoMath.distanceMeters(from, to),
            isFallback = true
        )
        legCache[cacheKey] = fallbackLeg
        return fallbackLeg
    }

    private fun createDirectLeg(from: LatLng, to: LatLng): RouteLeg {
        return RouteLeg(
            from = from,
            to = to,
            snappedPoints = listOf(from, to),
            distanceMeters = GeoMath.distanceMeters(from, to),
            isFallback = false
        )
    }
}
