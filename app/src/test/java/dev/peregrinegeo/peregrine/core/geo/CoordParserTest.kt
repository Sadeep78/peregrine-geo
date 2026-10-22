package dev.peregrinegeo.peregrine.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoordParserTest {

    private val delta = 0.001

    @Test
    fun testSimpleDecimal() {
        val result = CoordParser.parse("37.7749, -122.4194")
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testSimpleDecimalWithSpaceSeparator() {
        val result = CoordParser.parse("37.7749 -122.4194")
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testHemisphereDecimal() {
        val result = CoordParser.parse("37.7749° N, 122.4194° W")
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testHemispherePrefix() {
        val result = CoordParser.parse("N 37.7749, W 122.4194")
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testDmsCoordinates() {
        // 37° 46' 29.64" N = 37 + 46/60 + 29.64/3600 = 37.7749
        // 122° 25' 9.9" W = -(122 + 25/60 + 9.9/3600) = -122.4194
        val result = CoordParser.parse("""37° 46' 29.64" N, 122° 25' 9.9" W""")
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testGoogleMapsAtUrl() {
        val url = "https://www.google.com/maps/place/San+Francisco/@37.7749,-122.4194,15z"
        val result = CoordParser.parse(url)
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testGoogleMapsQueryUrl() {
        val url = "https://maps.google.com/?q=37.7749,-122.4194"
        val result = CoordParser.parse(url)
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testGeoUri() {
        val result = CoordParser.parse("geo:37.7749,-122.4194")
        assertNotNull(result)
        assertEquals(37.7749, result!!.latitude, delta)
        assertEquals(-122.4194, result.longitude, delta)
    }

    @Test
    fun testInvalidInputs() {
        assertNull(CoordParser.parse(null))
        assertNull(CoordParser.parse(""))
        assertNull(CoordParser.parse("     "))
        assertNull(CoordParser.parse("not a coordinate"))
        assertNull(CoordParser.parse("95.0, 12.0")) // lat > 90
        assertNull(CoordParser.parse("10.0, 200.0")) // lon > 180
    }
}
