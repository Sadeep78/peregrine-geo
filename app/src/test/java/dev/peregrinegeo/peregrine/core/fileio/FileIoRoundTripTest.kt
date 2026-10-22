package dev.peregrinegeo.peregrine.core.fileio

import dev.peregrinegeo.peregrine.core.geo.LatLng
import dev.peregrinegeo.peregrine.core.kinematics.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FileIoRoundTripTest {

    private val samplePoints = listOf(
        RoutePoint(lat = 37.774929, lon = -122.419416, ele = 15.0, timeMs = 1774000000000L),
        RoutePoint(lat = 37.775880, lon = -122.418420, ele = 18.5, timeMs = 1774000010000L),
        RoutePoint(lat = 37.776500, lon = -122.417200, ele = 22.0, timeMs = 1774000020000L)
    )

    @Test
    fun testGpxRoundTrip() {
        val exported = GpxFileIo.export(samplePoints, "Test GPX Track")
        assertTrue(exported.contains("<gpx"))
        assertTrue(exported.contains("trkpt"))

        val parsed = GpxFileIo.parse(exported)
        assertEquals(samplePoints.size, parsed.size)

        for (i in samplePoints.indices) {
            val exp = samplePoints[i]
            val act = parsed[i]
            assertEquals("Lat mismatch at $i", exp.lat, act.lat, 1e-5)
            assertEquals("Lon mismatch at $i", exp.lon, act.lon, 1e-5)
            assertEquals("Ele mismatch at $i", exp.ele!!, act.ele!!, 0.1)
            assertNotNull("Time must be present", act.timeMs)
            assertEquals("Time mismatch at $i", exp.timeMs!! / 1000L, act.timeMs!! / 1000L)
        }
    }

    @Test
    fun testKmlRoundTrip() {
        val exported = KmlFileIo.export(samplePoints, "Test KML Track")
        assertTrue(exported.contains("<kml"))
        assertTrue(exported.contains("<LineString>"))

        val parsed = KmlFileIo.parse(exported)
        assertEquals(samplePoints.size, parsed.size)

        for (i in samplePoints.indices) {
            val exp = samplePoints[i]
            val act = parsed[i]
            assertEquals("Lat mismatch at $i", exp.lat, act.lat, 1e-5)
            assertEquals("Lon mismatch at $i", exp.lon, act.lon, 1e-5)
            assertEquals("Ele mismatch at $i", exp.ele!!, act.ele!!, 0.1)
        }
    }

    @Test
    fun testGeoJsonRoundTrip() {
        val exported = GeoJsonFileIo.export(samplePoints, "Test GeoJSON Track")
        assertTrue(exported.contains(""""FeatureCollection""""))
        assertTrue(exported.contains(""""LineString""""))

        val parsed = GeoJsonFileIo.parse(exported)
        assertEquals(samplePoints.size, parsed.size)

        for (i in samplePoints.indices) {
            val exp = samplePoints[i]
            val act = parsed[i]
            assertEquals("Lat mismatch at $i", exp.lat, act.lat, 1e-5)
            assertEquals("Lon mismatch at $i", exp.lon, act.lon, 1e-5)
            assertEquals("Ele mismatch at $i", exp.ele!!, act.ele!!, 0.1)
            assertNotNull("Time must be present", act.timeMs)
            assertEquals("Time mismatch at $i", exp.timeMs!! / 1000L, act.timeMs!! / 1000L)
        }
    }

    @Test
    fun testXxeAttackPrevention() {
        // Malicious XXE injection payload attempting to read external entity
        val xxePayload = """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE gpx [
  <!ENTITY xxe SYSTEM "file:///etc/passwd">
]>
<gpx version="1.1">
  <trk><trkseg><trkpt lat="0.0" lon="0.0"><name>&xxe;</name></trkpt></trkseg></trk>
</gpx>"""

        try {
            GpxFileIo.parse(xxePayload)
            fail("Expected parser to reject DOCTYPE declaration (XXE defense)")
        } catch (_: Exception) {
            // Expected security exception due to disallow-doctype-decl
        }
    }

    @Test
    fun testDouglasPeuckerSimplification() {
        // Create 10 collinear points along a line
        val collinear = (0..10).map { LatLng(37.77 + it * 0.001, -122.41) }
        val simplified = DouglasPeucker.simplify(collinear, toleranceMeters = 5.0)

        // Collinear points should be reduced to start and end
        assertEquals(2, simplified.size)
        assertEquals(collinear.first(), simplified.first())
        assertEquals(collinear.last(), simplified.last())
    }
}
