package dev.peregrinegeo.peregrine.core.geo

import java.net.URI

data class RedirectResult(
    val statusCode: Int,
    val redirectLocation: String?
)

/**
 * Resolves shortened Google Maps URLs (goo.gl, maps.app.goo.gl) to extracted coordinates.
 *
 * Security & Reliability Requirements:
 * - Strict Host Allowlist: Rejects non-Google domains to prevent SSRF and tracking attacks.
 * - Max Redirect Limit: Aborts after at most 5 redirects.
 * - Timeout Enforcement: Aborts hanging connection attempts after timeoutMs.
 */
class MapsLinkResolver(
    val maxRedirects: Int = 5,
    val timeoutMs: Long = 5000L,
    private val httpGetFollowRedirect: ((url: String, timeoutMs: Long) -> RedirectResult?)? = null
) {
    companion object {
        val ALLOWED_HOSTS = setOf(
            "maps.google.com",
            "www.google.com",
            "google.com",
            "goo.gl",
            "maps.app.goo.gl"
        )
    }

    fun isHostAllowed(urlString: String): Boolean {
        return try {
            val uri = URI(urlString)
            val host = uri.host?.lowercase() ?: return false
            ALLOWED_HOSTS.contains(host) || ALLOWED_HOSTS.any { host.endsWith(".$it") }
        } catch (e: Exception) {
            false
        }
    }

    fun resolve(url: String): LatLng? {
        if (!isHostAllowed(url)) return null

        // If coordinates already present in original URL, parse immediately
        val directCoords = CoordParser.parse(url)
        if (directCoords != null) return directCoords

        var currentUrl = url
        var redirects = 0

        while (redirects < maxRedirects) {
            if (!isHostAllowed(currentUrl)) return null

            val result = httpGetFollowRedirect?.invoke(currentUrl, timeoutMs) ?: return null
            if (result.statusCode in 300..399 && result.redirectLocation != null) {
                currentUrl = result.redirectLocation
                redirects++

                // Try parsing each redirected location
                val coords = CoordParser.parse(currentUrl)
                if (coords != null) {
                    return coords
                }
            } else {
                break
            }
        }

        return CoordParser.parse(currentUrl)
    }
}
