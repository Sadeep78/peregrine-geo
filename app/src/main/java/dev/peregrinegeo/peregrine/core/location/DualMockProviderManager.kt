package dev.peregrinegeo.peregrine.core.location

import dev.peregrinegeo.peregrine.core.geo.LatLng

data class MockLocationSample(
    val point: LatLng,
    val speedMps: Double,
    val bearingDeg: Double,
    val altitudeMeters: Double = 15.0,
    val gpsAccuracyMeters: Double = 2.0,
    val networkAccuracyMeters: Double = 15.0,
    val timeMs: Long = System.currentTimeMillis(),
    val elapsedRealtimeNanos: Long = System.nanoTime()
)

data class InjectedLocationRecord(
    val provider: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Double,
    val bearing: Double,
    val accuracy: Double,
    val timeMs: Long,
    val elapsedRealtimeNanos: Long
)

/**
 * Interface abstracting Android OS framework and Play Services location injection.
 * Enables unit testing and verified test coverage without live Android hardware.
 */
interface MockLocationInjector {
    fun setupProvider(name: String, requiresNetwork: Boolean, requiresSatellite: Boolean, requiresCell: Boolean)
    fun setProviderEnabled(name: String, enabled: Boolean)
    fun injectProviderLocation(name: String, location: InjectedLocationRecord)
    fun removeProvider(name: String)
    fun setFusedMockMode(enabled: Boolean)
    fun injectFusedLocation(location: InjectedLocationRecord)
}

/**
 * Coordinates dual-injection into both LocationManager (GPS + NETWORK)
 * and Google Play Services FusedLocationProviderClient.
 */
class DualMockProviderManager(
    private val injector: MockLocationInjector,
    var defaultNetworkAccuracyMeters: Double = 15.0
) {
    companion object {
        const val GPS_PROVIDER = "gps"
        const val NETWORK_PROVIDER = "network"
    }

    var isStarted: Boolean = false
        private set

    /**
     * Initializes test providers on LocationManager and enables Fused mock mode.
     * Throws SecurityException if app is not designated as mock location app.
     */
    fun start() {
        try {
            // 1. Setup GPS test provider
            injector.setupProvider(
                name = GPS_PROVIDER,
                requiresNetwork = false,
                requiresSatellite = true,
                requiresCell = false
            )
            injector.setProviderEnabled(GPS_PROVIDER, true)

            // 2. Setup NETWORK test provider
            injector.setupProvider(
                name = NETWORK_PROVIDER,
                requiresNetwork = true,
                requiresSatellite = false,
                requiresCell = true
            )
            injector.setProviderEnabled(NETWORK_PROVIDER, true)

            // 3. Enable Fused location mock mode
            injector.setFusedMockMode(true)

            isStarted = true
        } catch (e: SecurityException) {
            stop()
            throw e
        } catch (e: Exception) {
            stop()
            throw IllegalStateException("Failed to initialize mock providers: ${e.message}", e)
        }
    }

    /**
     * Injects a timestamped sample into all providers simultaneously.
     * Both LocationManager providers and Fused client receive the exact same timestamp.
     */
    fun injectSample(sample: MockLocationSample) {
        if (!isStarted) {
            throw IllegalStateException("Cannot inject sample: DualMockProviderManager has not been started")
        }

        val nowMs = sample.timeMs
        val nowNanos = sample.elapsedRealtimeNanos

        // 1. GPS provider fix
        val gpsFix = InjectedLocationRecord(
            provider = GPS_PROVIDER,
            latitude = sample.point.latitude,
            longitude = sample.point.longitude,
            altitude = sample.altitudeMeters,
            speed = sample.speedMps,
            bearing = sample.bearingDeg,
            accuracy = sample.gpsAccuracyMeters,
            timeMs = nowMs,
            elapsedRealtimeNanos = nowNanos
        )
        injector.injectProviderLocation(GPS_PROVIDER, gpsFix)

        // 2. Network provider fix (uses larger accuracy radius)
        val networkFix = InjectedLocationRecord(
            provider = NETWORK_PROVIDER,
            latitude = sample.point.latitude,
            longitude = sample.point.longitude,
            altitude = sample.altitudeMeters,
            speed = sample.speedMps,
            bearing = sample.bearingDeg,
            accuracy = sample.networkAccuracyMeters.coerceAtLeast(defaultNetworkAccuracyMeters),
            timeMs = nowMs,
            elapsedRealtimeNanos = nowNanos
        )
        injector.injectProviderLocation(NETWORK_PROVIDER, networkFix)

        // 3. FusedLocationProviderClient fix
        injector.injectFusedLocation(gpsFix)
    }

    /**
     * Stops mock injection, disables test providers, and removes them cleanly.
     */
    fun stop() {
        try {
            injector.setFusedMockMode(false)
        } catch (_: Exception) { }

        try {
            injector.setProviderEnabled(GPS_PROVIDER, false)
            injector.removeProvider(GPS_PROVIDER)
        } catch (_: Exception) { }

        try {
            injector.setProviderEnabled(NETWORK_PROVIDER, false)
            injector.removeProvider(NETWORK_PROVIDER)
        } catch (_: Exception) { }

        isStarted = false
    }
}
