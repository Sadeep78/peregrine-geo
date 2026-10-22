package dev.peregrinegeo.peregrine.core.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapsLinkResolverTest {

    @Test
    fun `host allowlist rejects non-allowlisted domains without network requests`() {
        var networkAttempted = false
        val resolver = MapsLinkResolver(
            httpGetFollowRedirect = { _, _ ->
                networkAttempted = true
                null
            }
        )

        assertFalse(resolver.isHostAllowed("https://evil.com/shortlink"))
        assertFalse(resolver.isHostAllowed("https://malicious-site.org/maps"))
        assertFalse(resolver.isHostAllowed("http://169.254.169.254/latest"))

        val result = resolver.resolve("https://evil.com/shortlink")
        assertNull("Non-allowlisted URL must return null", result)
        assertFalse("No network request must be made to non-allowlisted hosts", networkAttempted)
    }

    @Test
    fun `host allowlist accepts standard Google Maps domains`() {
        val resolver = MapsLinkResolver()
        assertTrue(resolver.isHostAllowed("https://maps.app.goo.gl/abcdef"))
        assertTrue(resolver.isHostAllowed("https://goo.gl/maps/xyz123"))
        assertTrue(resolver.isHostAllowed("https://maps.google.com/?q=37.7,-122.4"))
        assertTrue(resolver.isHostAllowed("https://www.google.com/maps/@37.7,-122.4,15z"))
    }

    @Test
    fun `redirects exceeding limit of 5 are safely aborted`() {
        var redirectCount = 0
        val resolver = MapsLinkResolver(
            maxRedirects = 5,
            httpGetFollowRedirect = { url, _ ->
                redirectCount++
                // Continuously redirect to another allowed URL
                RedirectResult(statusCode = 302, redirectLocation = "https://maps.app.goo.gl/loop-$redirectCount")
            }
        )

        val result = resolver.resolve("https://maps.app.goo.gl/start")
        assertNull("Infinite or excessive redirects (> 5) must be aborted", result)
        assertEquals(5, redirectCount)
    }

    @Test
    fun `successful shortlink resolution extracts coordinates`() {
        val resolver = MapsLinkResolver(
            httpGetFollowRedirect = { url, _ ->
                if (url == "https://maps.app.goo.gl/validShort") {
                    RedirectResult(
                        statusCode = 302,
                        redirectLocation = "https://www.google.com/maps/place/Data/@37.774929,-122.419416,17z"
                    )
                } else {
                    null
                }
            }
        )

        val result = resolver.resolve("https://maps.app.goo.gl/validShort")
        assertNotNull(result)
        assertEquals(37.774929, result!!.latitude, 0.0001)
        assertEquals(-122.419416, result.longitude, 0.0001)
    }
}
