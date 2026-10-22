package dev.peregrinegeo.peregrine.core.datastore

import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.location.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun testDefaults() {
        val settings = InMemorySettingsRepository()
        assertEquals(AppOpeningBehavior.LastView, settings.appOpeningBehavior)
        assertEquals(RouteRecoveryPolicy.Ask, settings.routeRecoveryPolicy)
        assertNull(settings.activeRouteCheckpoint)
        assertFalse(settings.shouldPromptForRecovery())
        assertFalse(settings.shouldAutoResume())
    }

    @Test
    fun testRecoveryPromptWhenPolicyIsAsk() {
        val settings = InMemorySettingsRepository()
        settings.routeRecoveryPolicy = RouteRecoveryPolicy.Ask

        settings.activeRouteCheckpoint = PersistedRouteCheckpoint(
            routeId = "route-1",
            routeName = "Downtown Route",
            waypoints = listOf(LatLng(10.0, 10.0), LatLng(10.1, 10.1)),
            playMode = PlayMode.Once,
            speedMps = 5.0,
            speedMultiplier = 1.0,
            progressFraction = 0.45,
            distanceTraveledMeters = 500.0
        )

        assertTrue(settings.shouldPromptForRecovery())
        assertFalse(settings.shouldAutoResume())

        settings.clearRouteCheckpoint()
        assertNull(settings.activeRouteCheckpoint)
        assertFalse(settings.shouldPromptForRecovery())
    }

    @Test
    fun testAutoResumeWhenPolicyIsAuto() {
        val settings = InMemorySettingsRepository()
        settings.routeRecoveryPolicy = RouteRecoveryPolicy.Auto

        settings.activeRouteCheckpoint = PersistedRouteCheckpoint(
            routeId = "route-2",
            routeName = "Expressway",
            waypoints = listOf(LatLng(20.0, 20.0), LatLng(20.5, 20.5)),
            playMode = PlayMode.Loop,
            speedMps = 15.0,
            speedMultiplier = 1.0,
            progressFraction = 0.8,
            distanceTraveledMeters = 2000.0
        )

        assertFalse(settings.shouldPromptForRecovery())
        assertTrue(settings.shouldAutoResume())
    }

    @Test
    fun testOffPolicyIgnoresCheckpoint() {
        val settings = InMemorySettingsRepository()
        settings.routeRecoveryPolicy = RouteRecoveryPolicy.Off

        settings.activeRouteCheckpoint = PersistedRouteCheckpoint(
            routeId = "route-3",
            routeName = "Trail",
            waypoints = listOf(LatLng(30.0, 30.0), LatLng(30.1, 30.1)),
            playMode = PlayMode.Once,
            speedMps = 1.4,
            speedMultiplier = 1.0,
            progressFraction = 0.1,
            distanceTraveledMeters = 50.0
        )

        assertFalse(settings.shouldPromptForRecovery())
        assertFalse(settings.shouldAutoResume())
    }
}
