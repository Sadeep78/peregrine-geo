package dev.peregrinegeo.peregrine.feature.overlay

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Orientation reference modes for the floating joystick.
 */
enum class JoystickMode {
    /** Up = True North (0 degrees) */
    NORTH_RELATIVE,

    /** Up = Current vehicle forward heading */
    HEADING_RELATIVE,

    /** Snaps bearing to closest parallel or anti-parallel road direction */
    ROAD_SNAPPED
}

/**
 * Output of joystick deflection calculation.
 */
data class JoystickOutput(
    val speedMps: Double,
    val bearingDeg: Double,
    val deflectionFraction: Double,
    val inDeadZone: Boolean
)

/**
 * Pure Kotlin mathematical engine for floating joystick deflection,
 * dead-zone filtering, non-linear quadratic velocity scaling, and bearing calculation.
 */
object JoystickMath {

    const val DEFAULT_DEAD_ZONE_RATIO = 0.10 // 10% of radius

    /**
     * Calculates speed and bearing from Cartesian displacement (dx, dy) where
     * positive dy is downward (screen coordinates) and positive dx is rightward.
     *
     * @param dx Horizontal displacement in pixels from joystick center.
     * @param dy Vertical displacement in pixels from joystick center.
     * @param maxRadius Maximum joystick boundary radius in pixels.
     * @param maxSpeedMps Maximum velocity reachable at full deflection.
     * @param deadZoneRatio Dead zone radius fraction (default 0.10 = 10%).
     * @param mode Orientation mapping mode.
     * @param currentHeadingDeg Current vehicle heading in degrees (used in HEADING_RELATIVE mode).
     * @param roadBearingDeg Nearest road bearing in degrees (used in ROAD_SNAPPED mode).
     */
    fun calculate(
        dx: Double,
        dy: Double,
        maxRadius: Double,
        maxSpeedMps: Double,
        deadZoneRatio: Double = DEFAULT_DEAD_ZONE_RATIO,
        mode: JoystickMode = JoystickMode.NORTH_RELATIVE,
        currentHeadingDeg: Double = 0.0,
        roadBearingDeg: Double? = null
    ): JoystickOutput {
        require(maxRadius > 0.0) { "maxRadius must be > 0" }
        require(maxSpeedMps >= 0.0) { "maxSpeedMps must be >= 0" }

        val r = sqrt(dx * dx + dy * dy)
        val deadZoneRadius = maxRadius * deadZoneRatio

        // 1. Dead Zone Check
        if (r <= deadZoneRadius) {
            return JoystickOutput(
                speedMps = 0.0,
                bearingDeg = currentHeadingDeg,
                deflectionFraction = 0.0,
                inDeadZone = true
            )
        }

        // 2. Quadratic Velocity Curve: v = v_max * ((r - r_dead) / (R - r_dead))^2
        val activeRange = maxRadius - deadZoneRadius
        val normalizedDeflection = min(1.0, (r - deadZoneRadius) / activeRange)
        val speedMps = maxSpeedMps * normalizedDeflection.pow(2.0)

        // 3. Raw Angle Calculation (Screen coordinates: -dy is North/Up, +dx is East/Right)
        // atan2(dx, -dy) gives 0 at Up, +90 at Right, 180 at Down, -90 at Left
        val rawAngleRad = atan2(dx, -dy)
        val rawAngleDeg = (Math.toDegrees(rawAngleRad) + 360.0) % 360.0

        // 4. Mode-specific Bearing Transformation
        val effectiveBearingDeg = when (mode) {
            JoystickMode.NORTH_RELATIVE -> rawAngleDeg

            JoystickMode.HEADING_RELATIVE -> {
                (currentHeadingDeg + rawAngleDeg) % 360.0
            }

            JoystickMode.ROAD_SNAPPED -> {
                if (roadBearingDeg != null) {
                    snapToRoad(rawAngleDeg, roadBearingDeg)
                } else {
                    rawAngleDeg
                }
            }
        }

        return JoystickOutput(
            speedMps = speedMps,
            bearingDeg = effectiveBearingDeg,
            deflectionFraction = normalizedDeflection,
            inDeadZone = false
        )
    }

    /**
     * Snaps user joystick deflection to the nearest road bearing (forward or reverse).
     */
    fun snapToRoad(joystickAngleDeg: Double, roadBearingDeg: Double): Double {
        val forwardRoad = (roadBearingDeg + 360.0) % 360.0
        val reverseRoad = (roadBearingDeg + 180.0) % 360.0

        val diffForward = angularDifference(joystickAngleDeg, forwardRoad)
        val diffReverse = angularDifference(joystickAngleDeg, reverseRoad)

        return if (diffForward <= diffReverse) forwardRoad else reverseRoad
    }

    private fun angularDifference(a: Double, b: Double): Double {
        var diff = abs(a - b) % 360.0
        if (diff > 180.0) diff = 360.0 - diff
        return diff
    }
}
