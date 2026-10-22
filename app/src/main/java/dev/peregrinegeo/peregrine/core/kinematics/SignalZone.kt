package dev.peregrinegeo.peregrine.core.kinematics

import dev.peregrinegeo.peregrine.core.geo.GeoMath
import dev.peregrinegeo.peregrine.core.geo.LatLng
import kotlin.math.min

enum class DropoutBehavior {
    StopUpdates,
    RepeatLast,
    AccuracyBlowup
}

sealed interface SignalZoneMode {
    data class Degraded(
        val accuracyMultiplier: Double = 3.0,
        val extraJitterMeters: Double = 5.0
    ) : SignalZoneMode

    data class Dropout(
        val behavior: DropoutBehavior = DropoutBehavior.RepeatLast
    ) : SignalZoneMode

    data class Tunnel(
        val driftRateMps: Double = 0.5,
        val maxAccuracyBlowupMeters: Double = 100.0,
        val reacquisitionJumpMeters: Double = 8.0
    ) : SignalZoneMode
}

data class SignalZone(
    val id: String,
    val name: String,
    val startDistanceMeters: Double,
    val endDistanceMeters: Double,
    val fadeDistanceMeters: Double = 20.0,
    val mode: SignalZoneMode
) {
    init {
        require(startDistanceMeters >= 0.0) { "startDistance must be >= 0" }
        require(endDistanceMeters >= startDistanceMeters) { "endDistance must be >= startDistance" }
        require(fadeDistanceMeters >= 0.0) { "fadeDistance must be >= 0" }
    }

    fun containsDistance(distanceMeters: Double): Boolean {
        return distanceMeters in startDistanceMeters..endDistanceMeters
    }

    /**
     * Calculates the fade factor [0.0, 1.0] for smooth zone transitions.
     */
    fun fadeFactor(distanceMeters: Double): Double {
        if (!containsDistance(distanceMeters)) return 0.0
        if (fadeDistanceMeters <= 0.0) return 1.0

        val distFromStart = distanceMeters - startDistanceMeters
        val distFromEnd = endDistanceMeters - distanceMeters

        val startFade = (distFromStart / fadeDistanceMeters).coerceIn(0.0, 1.0)
        val endFade = (distFromEnd / fadeDistanceMeters).coerceIn(0.0, 1.0)

        return min(startFade, endFade)
    }
}

data class ZoneTransitionEvent(
    val zoneId: String,
    val zoneName: String,
    val transition: String, // "enter" or "exit"
    val distanceMeters: Double,
    val timestampMs: Long
)

data class ZoneEffectResult(
    val sample: NoisySample?,
    val isDropped: Boolean,
    val activeZoneId: String?
)

/**
 * Manages active signal zones, applies zone effects, and detects transitions.
 */
class SignalZoneEvaluator(val zones: List<SignalZone> = emptyList()) {

    private var activeZoneId: String? = null
    private var lastSample: NoisySample? = null
    private var tunnelTimeInSec: Double = 0.0

    val transitionEvents = mutableListOf<ZoneTransitionEvent>()

    fun evaluate(
        sample: NoisySample,
        distanceMeters: Double,
        dtSeconds: Double,
        timestampMs: Long
    ): ZoneEffectResult {
        val currentZone = zones.firstOrNull { it.containsDistance(distanceMeters) }

        // Transition detection
        if (currentZone?.id != activeZoneId) {
            if (activeZoneId != null) {
                val oldZone = zones.firstOrNull { it.id == activeZoneId }
                transitionEvents.add(
                    ZoneTransitionEvent(
                        zoneId = activeZoneId!!,
                        zoneName = oldZone?.name ?: "Unknown",
                        transition = "exit",
                        distanceMeters = distanceMeters,
                        timestampMs = timestampMs
                    )
                )
            }
            if (currentZone != null) {
                transitionEvents.add(
                    ZoneTransitionEvent(
                        zoneId = currentZone.id,
                        zoneName = currentZone.name,
                        transition = "enter",
                        distanceMeters = distanceMeters,
                        timestampMs = timestampMs
                    )
                )
            }
            activeZoneId = currentZone?.id
            tunnelTimeInSec = 0.0
        }

        if (currentZone == null) {
            lastSample = sample
            return ZoneEffectResult(sample = sample, isDropped = false, activeZoneId = null)
        }

        val fade = currentZone.fadeFactor(distanceMeters)

        when (val mode = currentZone.mode) {
            is SignalZoneMode.Degraded -> {
                val accMult = 1.0 + (mode.accuracyMultiplier - 1.0) * fade
                val extraJitter = mode.extraJitterMeters * fade
                val modAcc = sample.horizontalAccuracyMeters * accMult + extraJitter
                val modSample = sample.copy(horizontalAccuracyMeters = modAcc)
                lastSample = modSample
                return ZoneEffectResult(sample = modSample, isDropped = false, activeZoneId = currentZone.id)
            }

            is SignalZoneMode.Dropout -> {
                return when (mode.behavior) {
                    DropoutBehavior.StopUpdates -> {
                        ZoneEffectResult(sample = null, isDropped = true, activeZoneId = currentZone.id)
                    }
                    DropoutBehavior.RepeatLast -> {
                        val repeated = lastSample ?: sample
                        ZoneEffectResult(sample = repeated, isDropped = false, activeZoneId = currentZone.id)
                    }
                    DropoutBehavior.AccuracyBlowup -> {
                        val blownUp = sample.copy(horizontalAccuracyMeters = 500.0)
                        lastSample = blownUp
                        ZoneEffectResult(sample = blownUp, isDropped = false, activeZoneId = currentZone.id)
                    }
                }
            }

            is SignalZoneMode.Tunnel -> {
                tunnelTimeInSec += dtSeconds
                // Dead reckoning: accuracy uncertainty grows with time inside tunnel
                val growingAccuracy = min(
                    mode.maxAccuracyBlowupMeters,
                    sample.horizontalAccuracyMeters + tunnelTimeInSec * mode.driftRateMps * 5.0
                )
                val tunnelSample = sample.copy(horizontalAccuracyMeters = growingAccuracy)
                lastSample = tunnelSample
                return ZoneEffectResult(sample = tunnelSample, isDropped = false, activeZoneId = currentZone.id)
            }
        }
    }
}
