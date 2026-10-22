package dev.peregrinegeo.peregrine.core.kinematics

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.PlayMode
import kotlin.math.max
import kotlin.math.min

data class KinematicEngineOutput(
    val stepIndex: Long,
    val timeMs: Long,
    val elapsedRealtimeNanos: Long,
    val truePosition: LatLng,
    val trueSpeedMps: Double,
    val trueBearingDeg: Double,
    val noisyPosition: LatLng,
    val reportedSpeedMps: Double,
    val reportedBearingDeg: Double,
    val horizontalAccuracyMeters: Double,
    val verticalAccuracyMeters: Double,
    val distanceTraveledMeters: Double,
    val progressFraction: Double,
    val isCompleted: Boolean,
    val isDropped: Boolean = false,
    val activeZoneId: String? = null
)

/**
 * Pure Kotlin Kinematic Movement Engine.
 * Implements deterministic time-stepped simulation along planned speed profiles.
 */
class KinematicMovementEngine(
    val plannedRoute: PlannedRoute,
    val noiseModel: NoiseModel = NoiseModel(),
    val zoneEvaluator: SignalZoneEvaluator = SignalZoneEvaluator(),
    val timeSource: TimeSource = SystemTimeSource(),
    val tickHz: Int = 10,
    initialSpeedMultiplier: Double = 1.0
) {
    init {
        require(tickHz in 1..20) { "tickHz must be between 1 and 20, got: $tickHz" }
        require(initialSpeedMultiplier > 0.0) { "Speed multiplier must be > 0" }
    }

    val dtSeconds: Double = 1.0 / tickHz

    var currentDistanceMeters: Double = 0.0
        private set

    var currentSpeedMps: Double = 0.0
        private set

    var currentBearingDeg: Double = 0.0
        private set

    var speedMultiplier: Double = initialSpeedMultiplier
        private set

    var targetSpeedMultiplier: Double = initialSpeedMultiplier
        private set

    var stepIndex: Long = 0
        private set

    var isCompleted: Boolean = false
        private set

    private var pingPongForward: Boolean = true

    init {
        if (plannedRoute.vertices.size >= 2) {
            currentBearingDeg = plannedRoute.vertices[0].segmentBearingDeg
        }
    }

    /**
     * Advances simulation by one fixed tick dt = 1 / tickHz.
     */
    fun step(): KinematicEngineOutput {
        val totalDistance = plannedRoute.totalDistanceMeters
        val profile = plannedRoute.motionProfile

        if (totalDistance <= 0.0) {
            val pt = plannedRoute.vertices.firstOrNull()?.point ?: RoutePoint(0.0, 0.0)
            val latLng = LatLng(pt.lat, pt.lon, pt.ele)
            return KinematicEngineOutput(
                stepIndex = stepIndex++,
                timeMs = timeSource.currentTimeMillis(),
                elapsedRealtimeNanos = timeSource.elapsedRealtimeNanos(),
                truePosition = latLng,
                trueSpeedMps = 0.0,
                trueBearingDeg = 0.0,
                noisyPosition = latLng,
                reportedSpeedMps = 0.0,
                reportedBearingDeg = 0.0,
                horizontalAccuracyMeters = 1.0,
                verticalAccuracyMeters = 2.0,
                distanceTraveledMeters = 0.0,
                progressFraction = 1.0,
                isCompleted = true
            )
        }

        if (isCompleted && plannedRoute.playMode == PlayMode.Once) {
            return generateOutputAt(currentDistanceMeters, currentSpeedMps, currentBearingDeg, isDone = true)
        }

        // 1. Smoothly ramp speed multiplier if requested mid-route
        if (speedMultiplier != targetSpeedMultiplier) {
            val maxDeltaMult = (profile.aAcc * dtSeconds) / profile.vTarget
            if (speedMultiplier < targetSpeedMultiplier) {
                speedMultiplier = min(targetSpeedMultiplier, speedMultiplier + maxDeltaMult)
            } else {
                speedMultiplier = max(targetSpeedMultiplier, speedMultiplier - maxDeltaMult)
            }
        }

        // 2. Determine target velocity from planned profile at current distance
        val targetProfileSpeed = plannedRoute.speedLimitAtDistance(currentDistanceMeters) * speedMultiplier

        // Ramp current speed towards target profile speed within [ -aDec*dt, +aAcc*dt ]
        val maxAccStep = profile.aAcc * dtSeconds
        val maxDecStep = profile.aDec * dtSeconds

        if (currentSpeedMps < targetProfileSpeed) {
            currentSpeedMps = min(targetProfileSpeed, currentSpeedMps + maxAccStep)
        } else if (currentSpeedMps > targetProfileSpeed) {
            currentSpeedMps = max(targetProfileSpeed, currentSpeedMps - maxDecStep)
        }

        // 3. Integrate distance traveled
        val stepDistance = currentSpeedMps * dtSeconds

        when (plannedRoute.playMode) {
            PlayMode.Once -> {
                val remaining = totalDistance - currentDistanceMeters
                if (remaining <= max(0.2, stepDistance)) {
                    currentDistanceMeters = totalDistance
                    currentSpeedMps = 0.0
                    isCompleted = true
                } else {
                    currentDistanceMeters += stepDistance
                }
            }

            PlayMode.Loop -> {
                currentDistanceMeters += stepDistance
                if (currentDistanceMeters >= totalDistance) {
                    currentDistanceMeters %= totalDistance
                }
            }

            PlayMode.PingPong -> {
                if (pingPongForward) {
                    currentDistanceMeters += stepDistance
                    if (currentDistanceMeters >= totalDistance) {
                        currentDistanceMeters = totalDistance - (currentDistanceMeters - totalDistance)
                        pingPongForward = false
                    }
                } else {
                    currentDistanceMeters -= stepDistance
                    if (currentDistanceMeters <= 0.0) {
                        currentDistanceMeters = -currentDistanceMeters
                        pingPongForward = true
                    }
                }
                currentDistanceMeters = currentDistanceMeters.coerceIn(0.0, totalDistance)
            }
        }

        return generateOutputAt(currentDistanceMeters, currentSpeedMps, currentBearingDeg, isCompleted)
    }

    fun setSpeedMultiplier(multiplier: Double) {
        require(multiplier > 0.0) { "Speed multiplier must be > 0" }
        targetSpeedMultiplier = multiplier
    }

    fun seek(fraction: Double) {
        val totalDistance = plannedRoute.totalDistanceMeters
        val clamped = fraction.coerceIn(0.0, 1.0)
        currentDistanceMeters = totalDistance * clamped
        currentSpeedMps = plannedRoute.speedLimitAtDistance(currentDistanceMeters) * speedMultiplier
        if (plannedRoute.playMode == PlayMode.Once && clamped >= 1.0) {
            isCompleted = true
            currentSpeedMps = 0.0
        } else {
            isCompleted = false
        }
    }

    private fun generateOutputAt(
        dist: Double,
        speed: Double,
        bearing: Double,
        isDone: Boolean
    ): KinematicEngineOutput {
        val totalDistance = plannedRoute.totalDistanceMeters
        val (truePos, segBearing) = interpolatePolyline(dist)

        val effectiveBearing = if (speed >= 0.5) {
            currentBearingDeg = if (pingPongForward) segBearing else (segBearing + 180.0) % 360.0
            currentBearingDeg
        } else {
            currentBearingDeg // Hold last bearing below 0.5 m/s
        }

        // Apply noise model
        val noisySample = noiseModel.applyNoise(
            truePoint = truePos,
            trueSpeedMps = speed,
            trueBearingDeg = effectiveBearing,
            dtSeconds = dtSeconds
        )

        // Evaluate signal zones
        val nowMs = timeSource.currentTimeMillis()
        val zoneEffect = zoneEvaluator.evaluate(
            sample = noisySample,
            distanceMeters = dist,
            dtSeconds = dtSeconds,
            timestampMs = nowMs
        )

        val finalSample = zoneEffect.sample ?: noisySample

        return KinematicEngineOutput(
            stepIndex = stepIndex++,
            timeMs = nowMs,
            elapsedRealtimeNanos = timeSource.elapsedRealtimeNanos(),
            truePosition = truePos,
            trueSpeedMps = speed,
            trueBearingDeg = effectiveBearing,
            noisyPosition = finalSample.point,
            reportedSpeedMps = finalSample.speedMps,
            reportedBearingDeg = finalSample.bearingDeg,
            horizontalAccuracyMeters = finalSample.horizontalAccuracyMeters,
            verticalAccuracyMeters = finalSample.verticalAccuracyMeters,
            distanceTraveledMeters = dist,
            progressFraction = if (totalDistance > 0.0) dist / totalDistance else 1.0,
            isCompleted = isDone,
            isDropped = zoneEffect.isDropped,
            activeZoneId = zoneEffect.activeZoneId
        )
    }

    private fun interpolatePolyline(dist: Double): Pair<LatLng, Double> {
        val vertices = plannedRoute.vertices
        if (vertices.isEmpty()) return Pair(LatLng(0.0, 0.0), 0.0)
        if (vertices.size == 1) return Pair(LatLng(vertices[0].point.lat, vertices[0].point.lon), 0.0)

        val targetDist = dist.coerceIn(0.0, plannedRoute.totalDistanceMeters)

        for (i in 0 until vertices.size - 1) {
            val d1 = vertices[i].cumulativeDistanceMeters
            val d2 = vertices[i + 1].cumulativeDistanceMeters
            if (targetDist in d1..d2) {
                val segLen = d2 - d1
                val fraction = if (segLen > 0.0) (targetDist - d1) / segLen else 0.0
                val p1 = LatLng(vertices[i].point.lat, vertices[i].point.lon, vertices[i].point.ele)
                val p2 = LatLng(vertices[i + 1].point.lat, vertices[i + 1].point.lon, vertices[i + 1].point.ele)
                val interpolated = GeoMath.interpolate(p1, p2, fraction)
                val bearing = vertices[i].segmentBearingDeg
                return Pair(interpolated, bearing)
            }
        }

        val last = vertices.last()
        return Pair(LatLng(last.point.lat, last.point.lon, last.point.ele), vertices[vertices.size - 2].segmentBearingDeg)
    }
}
