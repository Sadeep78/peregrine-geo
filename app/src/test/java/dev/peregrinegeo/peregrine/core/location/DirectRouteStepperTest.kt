package dev.peregrinegeo.peregrine.core.location

import dev.peregrinegeo.peregrine.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectRouteStepperTest {

    private val waypoints = listOf(
        LatLng(37.7749, -122.4194),
        LatLng(37.7759, -122.4194),
        LatLng(37.7769, -122.4194)
    )

    @Test
    fun testOnceModeCompletesAtEnd() {
        val stepper = DirectRouteStepper(waypoints, baseSpeedMps = 10.0, playMode = PlayMode.Once)
        val totalDist = stepper.totalDistanceMeters
        assertTrue("Total distance must be positive", totalDist > 0.0)

        // Step 1 second
        val step1 = stepper.step(1.0)
        assertFalse(step1.isCompleted)
        assertEquals(10.0, step1.distanceTraveledMeters, 0.01)

        // Advance past the total distance
        val stepEnd = stepper.step(100.0)
        assertTrue("Once mode should be completed", stepEnd.isCompleted)
        assertEquals(totalDist, stepEnd.distanceTraveledMeters, 0.01)
        assertEquals(0.0, stepEnd.speed, 0.001)
        assertEquals(1.0, stepEnd.progressFraction, 0.001)
    }

    @Test
    fun testLoopModeNeverCompletes() {
        val stepper = DirectRouteStepper(waypoints, baseSpeedMps = 20.0, playMode = PlayMode.Loop)
        val totalDist = stepper.totalDistanceMeters

        // Step past the total distance
        val result = stepper.step((totalDist / 20.0) * 2.5)
        assertFalse("Loop mode should never complete", result.isCompleted)
        assertTrue("Distance should wrap modulo totalDistance", result.distanceTraveledMeters < totalDist)
    }

    @Test
    fun testPingPongReversesDirection() {
        val stepper = DirectRouteStepper(waypoints, baseSpeedMps = 10.0, playMode = PlayMode.PingPong)
        val totalDist = stepper.totalDistanceMeters

        // Advance to 80%
        val timeTo80Pct = (totalDist * 0.8) / 10.0
        val res1 = stepper.step(timeTo80Pct)
        assertEquals(0.8, res1.progressFraction, 0.05)

        // Advance past end (to trigger reverse)
        stepper.step(timeTo80Pct)
        // Now distance should be decreasing
        val prevDist = stepper.currentDistanceMeters
        stepper.step(1.0)
        val nextDist = stepper.currentDistanceMeters
        assertTrue("PingPong distance should decrease after reversing", nextDist < prevDist)
    }

    @Test
    fun testSpeedMultiplier() {
        val stepper = DirectRouteStepper(waypoints, baseSpeedMps = 5.0, speedMultiplier = 2.0)
        assertEquals(10.0, stepper.effectiveSpeedMps, 0.001)

        val res = stepper.step(1.0)
        assertEquals(10.0, res.distanceTraveledMeters, 0.01)

        stepper.speedMultiplier = 3.0
        assertEquals(15.0, stepper.effectiveSpeedMps, 0.001)
    }

    @Test
    fun testSeek() {
        val stepper = DirectRouteStepper(waypoints, baseSpeedMps = 5.0)
        val res = stepper.seek(0.5)
        assertEquals(0.5, res.progressFraction, 0.001)
        assertEquals(stepper.totalDistanceMeters * 0.5, res.distanceTraveledMeters, 0.01)
    }
}
