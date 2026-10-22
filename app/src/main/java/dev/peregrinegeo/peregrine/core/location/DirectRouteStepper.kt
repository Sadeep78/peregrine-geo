package dev.peregrinegeo.peregrine.core.location

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.geo.LatLng

enum class PlayMode {
    Once,
    Loop,
    PingPong
}

data class RouteStepResult(
    val position: LatLng,
    val bearing: Double,
    val speed: Double,
    val progressFraction: Double,
    val isCompleted: Boolean,
    val distanceTraveledMeters: Double
)

/**
 * Stepper for direct constant-speed route playback.
 * Supports Once, Loop, and PingPong play modes.
 */
class DirectRouteStepper(
    val waypoints: List<LatLng>,
    val baseSpeedMps: Double = 5.0,
    var speedMultiplier: Double = 1.0,
    val playMode: PlayMode = PlayMode.Once
) {
    init {
        require(waypoints.size >= 2) { "Route must contain at least 2 waypoints" }
        require(baseSpeedMps > 0.0) { "Base speed must be > 0" }
        require(speedMultiplier > 0.0) { "Speed multiplier must be > 0" }
    }

    // Segment lengths and cumulative distances
    val segmentLengths = mutableListOf<Double>()
    val cumulativeDistances = mutableListOf<Double>()
    val totalDistanceMeters: Double

    init {
        cumulativeDistances.add(0.0)
        var sum = 0.0
        for (i in 0 until waypoints.size - 1) {
            val dist = GeoMath.distanceMeters(waypoints[i], waypoints[i + 1])
            segmentLengths.add(dist)
            sum += dist
            cumulativeDistances.add(sum)
        }
        totalDistanceMeters = sum
    }

    var currentDistanceMeters: Double = 0.0
        private set

    private var pingPongForward: Boolean = true
    var isCompleted: Boolean = false
        private set

    val effectiveSpeedMps: Double
        get() = if (isCompleted) 0.0 else baseSpeedMps * speedMultiplier

    /**
     * Advance the route by elapsed seconds dt.
     */
    fun step(dtSeconds: Double): RouteStepResult {
        if (totalDistanceMeters <= 0.0) {
            return RouteStepResult(
                position = waypoints.first(),
                bearing = 0.0,
                speed = 0.0,
                progressFraction = 1.0,
                isCompleted = true,
                distanceTraveledMeters = 0.0
            )
        }

        if (isCompleted && playMode == PlayMode.Once) {
            return evaluateAt(totalDistanceMeters, forward = true)
        }

        val stepDistance = effectiveSpeedMps * dtSeconds

        when (playMode) {
            PlayMode.Once -> {
                currentDistanceMeters += stepDistance
                if (currentDistanceMeters >= totalDistanceMeters) {
                    currentDistanceMeters = totalDistanceMeters
                    isCompleted = true
                }
                return evaluateAt(currentDistanceMeters, forward = true)
            }

            PlayMode.Loop -> {
                currentDistanceMeters += stepDistance
                currentDistanceMeters %= totalDistanceMeters
                return evaluateAt(currentDistanceMeters, forward = true)
            }

            PlayMode.PingPong -> {
                if (pingPongForward) {
                    currentDistanceMeters += stepDistance
                    if (currentDistanceMeters >= totalDistanceMeters) {
                        currentDistanceMeters = totalDistanceMeters - (currentDistanceMeters - totalDistanceMeters)
                        pingPongForward = false
                    }
                } else {
                    currentDistanceMeters -= stepDistance
                    if (currentDistanceMeters <= 0.0) {
                        currentDistanceMeters = -currentDistanceMeters
                        pingPongForward = true
                    }
                }
                currentDistanceMeters = currentDistanceMeters.coerceIn(0.0, totalDistanceMeters)
                return evaluateAt(currentDistanceMeters, forward = pingPongForward)
            }
        }
    }

    /**
     * Seek route to specific fraction [0.0, 1.0].
     */
    fun seek(fraction: Double): RouteStepResult {
        val clamped = fraction.coerceIn(0.0, 1.0)
        currentDistanceMeters = totalDistanceMeters * clamped
        if (playMode == PlayMode.Once && clamped >= 1.0) {
            isCompleted = true
        } else if (isCompleted && clamped < 1.0) {
            isCompleted = false
        }
        return evaluateAt(currentDistanceMeters, forward = pingPongForward)
    }

    /**
     * Reset simulation to starting position.
     */
    fun reset() {
        currentDistanceMeters = 0.0
        pingPongForward = true
        isCompleted = false
    }

    private fun evaluateAt(dist: Double, forward: Boolean): RouteStepResult {
        val targetDist = dist.coerceIn(0.0, totalDistanceMeters)

        // Find segment
        var segIndex = 0
        while (segIndex < segmentLengths.size - 1 && targetDist >= cumulativeDistances[segIndex + 1]) {
            segIndex++
        }

        val segStartDist = cumulativeDistances[segIndex]
        val segLen = segmentLengths[segIndex]
        val segFraction = if (segLen > 0.0) {
            ((targetDist - segStartDist) / segLen).coerceIn(0.0, 1.0)
        } else 0.0

        val p1 = waypoints[segIndex]
        val p2 = waypoints[segIndex + 1]

        val position = GeoMath.interpolate(p1, p2, segFraction)
        val bearing = if (forward) {
            GeoMath.initialBearing(p1, p2)
        } else {
            (GeoMath.initialBearing(p2, p1) + 360.0) % 360.0
        }

        val progress = if (totalDistanceMeters > 0.0) {
            targetDist / totalDistanceMeters
        } else 1.0

        return RouteStepResult(
            position = position,
            bearing = bearing,
            speed = if (isCompleted) 0.0 else effectiveSpeedMps,
            progressFraction = progress,
            isCompleted = isCompleted,
            distanceTraveledMeters = targetDist
        )
    }
}
