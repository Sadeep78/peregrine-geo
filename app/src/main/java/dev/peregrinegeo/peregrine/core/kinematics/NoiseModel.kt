package dev.peregrinegeo.peregrine.core.kinematics

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.geo.LatLng
import java.util.Random
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

data class NoisePreset(
    val name: String,
    val sigmaMeters: Double,
    val whiteJitterMeters: Double,
    val correlationTimeTauSeconds: Double = 20.0,
    val multipathBurstProbability: Double = 0.0,
    val multipathMaxMeters: Double = 0.0
) {
    companion object {
        val Ideal = NoisePreset(
            name = "Ideal",
            sigmaMeters = 0.0,
            whiteJitterMeters = 0.0
        )

        val OpenSky = NoisePreset(
            name = "OpenSky",
            sigmaMeters = 1.5,
            whiteJitterMeters = 0.3
        )

        val Suburban = NoisePreset(
            name = "Suburban",
            sigmaMeters = 3.0,
            whiteJitterMeters = 0.6
        )

        val UrbanCanyon = NoisePreset(
            name = "UrbanCanyon",
            sigmaMeters = 5.0,
            whiteJitterMeters = 1.0,
            multipathBurstProbability = 0.05,
            multipathMaxMeters = 15.0
        )
    }
}

data class NoisySample(
    val point: LatLng,
    val speedMps: Double,
    val bearingDeg: Double,
    val horizontalAccuracyMeters: Double,
    val verticalAccuracyMeters: Double
)

/**
 * Pure Kotlin first-order Gauss-Markov (Ornstein-Uhlenbeck) GNSS noise model.
 * Deterministic for a given seed.
 */
class NoiseModel(
    val preset: NoisePreset = NoisePreset.OpenSky,
    val seed: Long = 42L
) {
    private val random = Random(seed)

    private var eastErrorMeters: Double = 0.0
    private var northErrorMeters: Double = 0.0
    private var altErrorMeters: Double = 0.0
    private var lastBearingDeg: Double = 0.0

    /**
     * Applies noise model to true simulated state.
     */
    fun applyNoise(
        truePoint: LatLng,
        trueSpeedMps: Double,
        trueBearingDeg: Double,
        dtSeconds: Double
    ): NoisySample {
        if (preset.sigmaMeters <= 0.0 && preset.whiteJitterMeters <= 0.0) {
            val bearing = if (trueSpeedMps >= 0.5) trueBearingDeg else lastBearingDeg
            lastBearingDeg = bearing
            return NoisySample(
                point = truePoint,
                speedMps = trueSpeedMps,
                bearingDeg = bearing,
                horizontalAccuracyMeters = 1.0,
                verticalAccuracyMeters = 2.0
            )
        }

        // 1. Ornstein-Uhlenbeck process step: x_{t+dt} = x_t * exp(-dt/tau) + driving_noise
        val tau = max(0.1, preset.correlationTimeTauSeconds)
        val alpha = exp(-dtSeconds / tau)
        val drivingSigma = preset.sigmaMeters * sqrt(max(0.0, 1.0 - alpha * alpha))

        // White jitter component
        val jitterEast = random.nextGaussian() * preset.whiteJitterMeters
        val jitterNorth = random.nextGaussian() * preset.whiteJitterMeters

        eastErrorMeters = alpha * eastErrorMeters + drivingSigma * random.nextGaussian() + jitterEast
        northErrorMeters = alpha * northErrorMeters + drivingSigma * random.nextGaussian() + jitterNorth
        altErrorMeters = alpha * altErrorMeters + drivingSigma * random.nextGaussian() * 1.5

        // Multipath burst check
        if (preset.multipathBurstProbability > 0.0 && random.nextDouble() < preset.multipathBurstProbability) {
            val burstAngle = random.nextDouble() * 2.0 * Math.PI
            val burstDist = random.nextDouble() * preset.multipathMaxMeters
            eastErrorMeters += burstDist * kotlin.math.cos(burstAngle)
            northErrorMeters += burstDist * kotlin.math.sin(burstAngle)
        }

        // 2. Convert ENU error to Lat/Lon offsets
        val latRad = Math.toRadians(truePoint.latitude)
        val dLat = Math.toDegrees(northErrorMeters / GeoMath.EARTH_RADIUS_METERS)
        val dLon = Math.toDegrees(eastErrorMeters / (GeoMath.EARTH_RADIUS_METERS * cos(latRad)))

        val noisyLat = (truePoint.latitude + dLat).coerceIn(-90.0, 90.0)
        val noisyLon = ((truePoint.longitude + dLon + 540.0) % 360.0) - 180.0
        val baseAlt = truePoint.altitude ?: 10.0
        val noisyAlt = baseAlt + altErrorMeters

        // 3. Realistic dynamic reported accuracy (consistent with injected error, non-constant)
        val instantaneousError = sqrt(eastErrorMeters * eastErrorMeters + northErrorMeters * northErrorMeters)
        val horizontalAccuracy = (1.5 * preset.sigmaMeters + 0.3 * instantaneousError + 0.2 * abs(random.nextGaussian()))
            .coerceIn(1.0, 50.0)
        val verticalAccuracy = (horizontalAccuracy * 1.5).coerceIn(1.5, 75.0)

        // 4. Bearing and speed noise
        val speedNoise = random.nextGaussian() * 0.1
        val noisySpeed = max(0.0, trueSpeedMps + speedNoise)

        val effectiveBearing = if (trueSpeedMps >= 0.5) {
            lastBearingDeg = trueBearingDeg
            trueBearingDeg
        } else {
            lastBearingDeg // Below 0.5 m/s hold the last bearing
        }

        return NoisySample(
            point = LatLng(noisyLat, noisyLon, noisyAlt),
            speedMps = noisySpeed,
            bearingDeg = effectiveBearing,
            horizontalAccuracyMeters = horizontalAccuracy,
            verticalAccuracyMeters = verticalAccuracy
        )
    }

    private fun abs(value: Double): Double = kotlin.math.abs(value)
}
