package dev.peregrinegeo.peregrine.core.kinematics

/**
 * Single waypoint along a simulated route.
 * Pure Kotlin data structure.
 */
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val ele: Double? = null,
    val timeMs: Long? = null
) {
    init {
        require(lat in -90.0..90.0) { "Latitude must be between -90 and 90, got: $lat" }
        require(lon in -180.0..180.0) { "Longitude must be between -180 and 180, got: $lon" }
    }
}

/**
 * Kinematic constraints and target parameters for a motion profile.
 */
data class MotionProfile(
    val name: String,
    val vTarget: Double,
    val aAcc: Double,
    val aDec: Double,
    val aLatMax: Double,
    val vCorner: Double
) {
    init {
        require(vTarget > 0.0) { "vTarget must be > 0" }
        require(aAcc > 0.0) { "aAcc must be > 0" }
        require(aDec > 0.0) { "aDec must be > 0" }
        require(aLatMax > 0.0) { "aLatMax must be > 0" }
        require(vCorner > 0.0) { "vCorner must be > 0" }
    }

    companion object {
        val Walk = MotionProfile(
            name = "Walk",
            vTarget = 1.4,
            aAcc = 0.8,
            aDec = 1.2,
            aLatMax = 1.5,
            vCorner = 1.0
        )

        val Run = MotionProfile(
            name = "Run",
            vTarget = 3.5,
            aAcc = 1.5,
            aDec = 2.0,
            aLatMax = 3.0,
            vCorner = 2.0
        )

        val Bike = MotionProfile(
            name = "Bike",
            vTarget = 6.0,
            aAcc = 1.0,
            aDec = 2.5,
            aLatMax = 2.5,
            vCorner = 3.0
        )

        val Drive = MotionProfile(
            name = "Drive",
            vTarget = 18.0,
            aAcc = 2.5,
            aDec = 4.0,
            aLatMax = 3.0,
            vCorner = 5.0
        )
    }
}

/**
 * Clock source interface to enable deterministic simulation testing.
 */
interface TimeSource {
    fun currentTimeMillis(): Long
    fun elapsedRealtimeNanos(): Long
}

class SystemTimeSource : TimeSource {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeNanos(): Long = System.nanoTime()
}

class SteppedTimeSource(
    private var currentMs: Long = 1774000000000L,
    private var currentNanos: Long = 1000000000L
) : TimeSource {
    override fun currentTimeMillis(): Long = currentMs
    override fun elapsedRealtimeNanos(): Long = currentNanos

    fun advance(deltaMs: Long) {
        currentMs += deltaMs
        currentNanos += deltaMs * 1_000_000L
    }
}
