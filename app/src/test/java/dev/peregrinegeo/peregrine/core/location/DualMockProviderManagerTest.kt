package dev.peregrinegeo.peregrine.core.location

import dev.peregrinegeo.peregrine.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecordingMockLocationInjector : MockLocationInjector {
    val enabledProviders = mutableSetOf<String>()
    val registeredProviders = mutableSetOf<String>()
    var fusedMockModeEnabled = false
    val injectedLocations = mutableListOf<InjectedLocationRecord>()
    val fusedInjectedLocations = mutableListOf<InjectedLocationRecord>()
    var throwSecurityExceptionOnSetup = false

    override fun setupProvider(name: String, requiresNetwork: Boolean, requiresSatellite: Boolean, requiresCell: Boolean) {
        if (throwSecurityExceptionOnSetup) {
            throw SecurityException("Mock location permission missing")
        }
        registeredProviders.add(name)
    }

    override fun setProviderEnabled(name: String, enabled: Boolean) {
        if (enabled) enabledProviders.add(name) else enabledProviders.remove(name)
    }

    override fun injectProviderLocation(name: String, location: InjectedLocationRecord) {
        injectedLocations.add(location)
    }

    override fun removeProvider(name: String) {
        registeredProviders.remove(name)
        enabledProviders.remove(name)
    }

    override fun setFusedMockMode(enabled: Boolean) {
        fusedMockModeEnabled = enabled
    }

    override fun injectFusedLocation(location: InjectedLocationRecord) {
        fusedInjectedLocations.add(location)
    }
}

class DualMockProviderManagerTest {

    @Test
    fun testStartAndDualInjection() {
        val injector = RecordingMockLocationInjector()
        val manager = DualMockProviderManager(injector, defaultNetworkAccuracyMeters = 20.0)

        manager.start()
        assertTrue(manager.isStarted)
        assertTrue(injector.registeredProviders.contains("gps"))
        assertTrue(injector.registeredProviders.contains("network"))
        assertTrue(injector.fusedMockModeEnabled)

        val point = LatLng(37.7749, -122.4194)
        val sample = MockLocationSample(
            point = point,
            speedMps = 5.0,
            bearingDeg = 90.0,
            altitudeMeters = 15.0,
            gpsAccuracyMeters = 2.5,
            networkAccuracyMeters = 20.0,
            timeMs = 1700000000000L,
            elapsedRealtimeNanos = 500000000000L
        )

        manager.injectSample(sample)

        // Verify LocationManager injections
        assertEquals(2, injector.injectedLocations.size)
        val gpsFix = injector.injectedLocations.first { it.provider == "gps" }
        val networkFix = injector.injectedLocations.first { it.provider == "network" }

        assertEquals(sample.point.latitude, gpsFix.latitude, 0.0001)
        assertEquals(sample.point.longitude, gpsFix.longitude, 0.0001)
        assertEquals(2.5, gpsFix.accuracy, 0.01)
        assertEquals(1700000000000L, gpsFix.timeMs)
        assertEquals(500000000000L, gpsFix.elapsedRealtimeNanos)

        assertEquals(sample.point.latitude, networkFix.latitude, 0.0001)
        assertEquals(sample.point.longitude, networkFix.longitude, 0.0001)
        assertEquals(20.0, networkFix.accuracy, 0.01)
        assertEquals(1700000000000L, networkFix.timeMs)
        assertEquals(500000000000L, networkFix.elapsedRealtimeNanos)

        // Verify Fused client injection
        assertEquals(1, injector.fusedInjectedLocations.size)
        val fusedFix = injector.fusedInjectedLocations[0]
        assertEquals(sample.point.latitude, fusedFix.latitude, 0.0001)
        assertEquals(1700000000000L, fusedFix.timeMs)

        // Stop
        manager.stop()
        assertFalse(manager.isStarted)
        assertFalse(injector.fusedMockModeEnabled)
        assertFalse(injector.registeredProviders.contains("gps"))
        assertFalse(injector.registeredProviders.contains("network"))
    }

    @Test
    fun testSecurityExceptionTriggersCleanFailure() {
        val injector = RecordingMockLocationInjector().apply {
            throwSecurityExceptionOnSetup = true
        }
        val manager = DualMockProviderManager(injector)

        try {
            manager.start()
            fail("Expected SecurityException")
        } catch (_: SecurityException) {
            assertFalse(manager.isStarted)
            assertFalse(injector.fusedMockModeEnabled)
        }
    }
}
