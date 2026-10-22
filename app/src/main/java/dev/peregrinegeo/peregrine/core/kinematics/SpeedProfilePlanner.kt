package dev.peregrinegeo.peregrine.core.kinematics

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.PlayMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class VertexProfile(
    val point: RoutePoint,
    val cumulativeDistanceMeters: Double,
    val segmentBearingDeg: Double,
    val turnAngleDeg: Double,
    val radiusOfCurvatureMeters: Double,
    val targetSpeedMps: Double,
    val plannedSpeedMps: Double
)

data class PlannedRoute(
    val vertices: List<VertexProfile>,
    val totalDistanceMeters: Double,
    val motionProfile: MotionProfile,
    val playMode: PlayMode
) {
    /**
     * Calculates the maximum allowable speed at an arbitrary distance along the route,
     * ensuring the vehicle can decelerate to enter the next vertex at or below its planned corner speed limit.
     */
    fun speedLimitAtDistance(distanceMeters: Double): Double {
        val clamped = distanceMeters.coerceIn(0.0, totalDistanceMeters)
        if (vertices.size < 2) return 0.0

        for (i in 0 until vertices.size - 1) {
            val d1 = vertices[i].cumulativeDistanceMeters
            val d2 = vertices[i + 1].cumulativeDistanceMeters
            if (clamped in d1..d2) {
                val remToNextVertex = d2 - clamped
                val vNext = vertices[i + 1].plannedSpeedMps
                val vBrake = sqrt(max(0.0, vNext * vNext + 2.0 * motionProfile.aDec * remToNextVertex))
                return min(motionProfile.vTarget, vBrake)
            }
        }
        return vertices.last().plannedSpeedMps
    }
}

/**
 * Pure Kotlin Speed Profile Planner executing forward and backward kinematic passes.
 */
object SpeedProfilePlanner {

    private const val MAX_CURVATURE_RADIUS = 10000.0

    fun plan(
        points: List<RoutePoint>,
        profile: MotionProfile,
        playMode: PlayMode = PlayMode.Once
    ): PlannedRoute {
        require(points.size >= 2) { "Route must have at least 2 points to plan speed profile" }

        val n = points.size
        val cumulativeDistances = DoubleArray(n)
        val segmentDistances = DoubleArray(n - 1)
        val segmentBearings = DoubleArray(n - 1)
        val turnAngles = DoubleArray(n)
        val curvatureRadii = DoubleArray(n)
        val vLim = DoubleArray(n)

        // 1. Calculate segment lengths and bearings
        var cumDist = 0.0
        cumulativeDistances[0] = 0.0
        for (i in 0 until n - 1) {
            val p1 = LatLng(points[i].lat, points[i].lon)
            val p2 = LatLng(points[i + 1].lat, points[i + 1].lon)
            val d = GeoMath.distanceMeters(p1, p2)
            segmentDistances[i] = d
            segmentBearings[i] = GeoMath.initialBearing(p1, p2)
            cumDist += d
            cumulativeDistances[i + 1] = cumDist
        }
        val totalDistance = cumDist

        // 2. Calculate turn angles and curvature radii for all vertices
        for (i in 0 until n) {
            if (i == 0 || i == n - 1) {
                turnAngles[i] = 0.0
                curvatureRadii[i] = MAX_CURVATURE_RADIUS
            } else {
                val bIn = segmentBearings[i - 1]
                val bOut = segmentBearings[i]
                var turn = abs(bOut - bIn)
                if (turn > 180.0) turn = 360.0 - turn
                turnAngles[i] = turn

                val pA = LatLng(points[i - 1].lat, points[i - 1].lon)
                val pB = LatLng(points[i].lat, points[i].lon)
                val pC = LatLng(points[i + 1].lat, points[i + 1].lon)
                curvatureRadii[i] = computeCircumradius(pA, pB, pC)
            }
        }

        // 3. Compute vertex speed limit: v_lim = min(v_target, sqrt(a_lat_max * R))
        // Turns sharper than 45 degrees receive corner cap v_corner
        for (i in 0 until n) {
            val r = curvatureRadii[i]
            val vLat = sqrt(max(0.0, profile.aLatMax * r))
            var cap = min(profile.vTarget, vLat)

            if (turnAngles[i] > 45.0) {
                cap = min(cap, profile.vCorner)
            }
            vLim[i] = cap
        }

        // 4. Backward Pass (enforce deceleration limits before corners and destination)
        val vPlanned = DoubleArray(n)
        if (playMode == PlayMode.Once) {
            vPlanned[n - 1] = 0.0 // Decelerate to stop at the end
        } else {
            vPlanned[n - 1] = vLim[n - 1]
        }

        for (i in n - 2 downTo 0) {
            val d = segmentDistances[i]
            val maxAllowedFromNext = sqrt(vPlanned[i + 1] * vPlanned[i + 1] + 2.0 * profile.aDec * d)
            vPlanned[i] = min(vLim[i], maxAllowedFromNext)
        }

        // 5. Forward Pass (enforce acceleration limits from start and out of corners)
        vPlanned[0] = vLim[0]
        for (i in 0 until n - 1) {
            val d = segmentDistances[i]
            val maxAllowedFromPrev = sqrt(vPlanned[i] * vPlanned[i] + 2.0 * profile.aAcc * d)
            vPlanned[i + 1] = min(vPlanned[i + 1], maxAllowedFromPrev)
        }

        // Build vertex profile list
        val vertexProfiles = mutableListOf<VertexProfile>()
        for (i in 0 until n) {
            val segBearing = if (i < n - 1) segmentBearings[i] else segmentBearings.lastOrNull() ?: 0.0
            vertexProfiles.add(
                VertexProfile(
                    point = points[i],
                    cumulativeDistanceMeters = cumulativeDistances[i],
                    segmentBearingDeg = segBearing,
                    turnAngleDeg = turnAngles[i],
                    radiusOfCurvatureMeters = curvatureRadii[i],
                    targetSpeedMps = vLim[i],
                    plannedSpeedMps = vPlanned[i]
                )
            )
        }

        return PlannedRoute(
            vertices = vertexProfiles,
            totalDistanceMeters = totalDistance,
            motionProfile = profile,
            playMode = playMode
        )
    }

    private fun computeCircumradius(a: LatLng, b: LatLng, c: LatLng): Double {
        val distA = GeoMath.distanceMeters(b, c)
        val distB = GeoMath.distanceMeters(a, c)
        val distC = GeoMath.distanceMeters(a, b)

        if (distA <= 0.0 || distB <= 0.0 || distC <= 0.0) return MAX_CURVATURE_RADIUS

        val s = (distA + distB + distC) / 2.0
        val areaSq = s * (s - distA) * (s - distB) * (s - distC)

        if (areaSq <= 1e-6) return MAX_CURVATURE_RADIUS // Collinear or straight line
        val area = sqrt(areaSq)

        val r = (distA * distB * distC) / (4.0 * area)
        return min(r, MAX_CURVATURE_RADIUS)
    }
}
