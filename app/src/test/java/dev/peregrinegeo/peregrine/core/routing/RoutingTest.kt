package dev.peregrinegeo.peregrine.core.routing

import dev.peregrinegeo.peregrine.core.geo.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRoutingBackend : RoutingBackend {
    var routeCallCount = 0
    var shouldFailLeg = false

    override fun fetchRoute(from: LatLng, to: LatLng, mode: SnapMode): RouteLegResult? {
        routeCallCount++
        if (shouldFailLeg) return null

        // Return a mock snapped intermediate point
        val mid = LatLng((from.latitude + to.latitude) / 2.0, (from.longitude + to.longitude) / 2.0)
        return RouteLegResult(
            points = listOf(from, mid, to),
            distanceMeters = 100.0
        )
    }
}

class RoutingTest {

    @Test
    fun testPolyline6EncodeDecodeRoundTrip() {
        val original = listOf(
            LatLng(37.774929, -122.419416),
            LatLng(37.775880, -122.418420),
            LatLng(37.776500, -122.417200)
        )

        val encoded = Polyline6.encode(original)
        assertTrue("Encoded string must not be empty", encoded.isNotEmpty())

        val decoded = Polyline6.decode(encoded)
        assertEquals(original.size, decoded.size)
        for (i in original.indices) {
            assertEquals(original[i].latitude, decoded[i].latitude, 1e-6)
            assertEquals(original[i].longitude, decoded[i].longitude, 1e-6)
        }
    }

    @Test
    fun testRouteSnapperDirectMode() {
        val snapper = RouteSnapper()
        val waypoints = listOf(
            LatLng(37.7749, -122.4194),
            LatLng(37.7759, -122.4194),
            LatLng(37.7769, -122.4194)
        )

        val result = snapper.planRoute(waypoints, SnapMode.Direct)
        assertEquals(2, result.legs.size)
        assertEquals(3, result.fullPath.size)
        assertFalse("Direct mode has no fallback legs", result.hasFallbackLegs)
        assertTrue(result.totalDistanceMeters > 0.0)
    }

    @Test
    fun testRouteSnapperWithBackendAndFallback() {
        val backend = FakeRoutingBackend()
        val snapper = RouteSnapper(backend)
        val waypoints = listOf(
            LatLng(10.0, 10.0),
            LatLng(10.1, 10.1)
        )

        // Successful routed leg
        val res1 = snapper.planRoute(waypoints, SnapMode.Driving)
        assertEquals(1, res1.legs.size)
        assertEquals(3, res1.fullPath.size) // includes intermediate point
        assertFalse(res1.hasFallbackLegs)
        assertEquals(1, backend.routeCallCount)

        // Fallback leg when backend fails
        backend.shouldFailLeg = true
        val res2 = snapper.planRoute(listOf(LatLng(20.0, 20.0), LatLng(20.1, 20.1)), SnapMode.Driving)
        assertEquals(1, res2.legs.size)
        assertTrue("Failed route should flag as fallback leg", res2.hasFallbackLegs)
        assertEquals(2, res2.fullPath.size) // straight line
    }

    @Test
    fun testRouteSnapperWaypointUpdateReusesCache() {
        val backend = FakeRoutingBackend()
        val snapper = RouteSnapper(backend)
        val waypoints = listOf(
            LatLng(1.0, 1.0),
            LatLng(2.0, 2.0),
            LatLng(3.0, 3.0)
        )

        snapper.planRoute(waypoints, SnapMode.Driving)
        val initialCalls = backend.routeCallCount
        assertEquals(2, initialCalls) // 2 legs

        // Update middle waypoint
        snapper.updateWaypoint(waypoints, index = 1, newPosition = LatLng(2.5, 2.5), mode = SnapMode.Driving)
        // Should have requested 2 new legs
        assertEquals(4, backend.routeCallCount)
    }
}
