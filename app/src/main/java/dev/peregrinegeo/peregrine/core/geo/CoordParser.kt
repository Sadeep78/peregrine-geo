package dev.peregrinegeo.peregrine.core.geo

import java.util.regex.Pattern

/**
 * Robust parser for geographic coordinates from various user and clipboard formats.
 *
 * Supported formats:
 * 1. Plain Decimal Degrees: "37.7749, -122.4194" or "37.7749 -122.4194"
 * 2. Decimal with Hemisphere: "37.7749° N, 122.4194° W", "N37.7749 W122.4194"
 * 3. Degrees Minutes Seconds (DMS): "37° 46' 29.64\" N, 122° 25' 9.9\" W"
 * 4. Google Maps & Geo URLs: "https://maps.google.com/?q=37.7749,-122.4194",
 *    "https://www.google.com/maps/@37.7749,-122.4194,15z", "geo:37.7749,-122.4194"
 */
object CoordParser {

    private val GOOGLE_MAPS_AT_PATTERN = Pattern.compile(
        "@(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)"
    )

    private val GOOGLE_MAPS_3D4D_PATTERN = Pattern.compile(
        "!3d(-?\\d+(?:\\.\\d+)?)!4d(-?\\d+(?:\\.\\d+)?)"
    )

    private val GOOGLE_MAPS_Q_PATTERN = Pattern.compile(
        "[?&](?:q|query)=(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)"
    )

    private val GEO_URI_PATTERN = Pattern.compile(
        "geo:(-?\\d+(?:\\.\\d+)?),(-?\\d+(?:\\.\\d+)?)"
    )

    private val DMS_PATTERN = Pattern.compile(
        """(?i)(\d+(?:\.\d+)?)\s*°\s*(\d+(?:\.\d+)?)\s*['′]\s*(\d+(?:\.\d+)?)\s*["″]?\s*([NSEW])"""
    )

    private val HEMISPHERE_DECIMAL_PATTERN = Pattern.compile(
        """(?i)([NS])\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*°?\s*([NS])"""
    )

    private val HEMISPHERE_LON_PATTERN = Pattern.compile(
        """(?i)([EW])\s*(\d+(?:\.\d+)?)|(\d+(?:\.\d+)?)\s*°?\s*([EW])"""
    )

    private val SIMPLE_DECIMAL_PATTERN = Pattern.compile(
        """^(-?\d+(?:\.\d+)?)[,\s]+(-?\d+(?:\.\d+)?)$"""
    )

    /**
     * Attempts to parse raw string into a [LatLng].
     * Returns null if string format is unrecognized or invalid.
     */
    fun parse(input: String?): LatLng? {
        if (input.isNullOrBlank()) return null
        val trimmed = input.trim()

        // 1. Check Google Maps @lat,lon pattern
        val atMatcher = GOOGLE_MAPS_AT_PATTERN.matcher(trimmed)
        if (atMatcher.find()) {
            val lat = atMatcher.group(1)?.toDoubleOrNull()
            val lon = atMatcher.group(2)?.toDoubleOrNull()
            if (isValid(lat, lon)) return LatLng(lat!!, lon!!)
        }

        // 1b. Check Google Maps !3d!4d pattern
        val d3d4Matcher = GOOGLE_MAPS_3D4D_PATTERN.matcher(trimmed)
        if (d3d4Matcher.find()) {
            val lat = d3d4Matcher.group(1)?.toDoubleOrNull()
            val lon = d3d4Matcher.group(2)?.toDoubleOrNull()
            if (isValid(lat, lon)) return LatLng(lat!!, lon!!)
        }

        // 2. Check query parameter pattern (?q=lat,lon or ?query=lat,lon)
        val qMatcher = GOOGLE_MAPS_Q_PATTERN.matcher(trimmed)
        if (qMatcher.find()) {
            val lat = qMatcher.group(1)?.toDoubleOrNull()
            val lon = qMatcher.group(2)?.toDoubleOrNull()
            if (isValid(lat, lon)) return LatLng(lat!!, lon!!)
        }

        // 3. Check geo: URI pattern
        val geoMatcher = GEO_URI_PATTERN.matcher(trimmed)
        if (geoMatcher.find()) {
            val lat = geoMatcher.group(1)?.toDoubleOrNull()
            val lon = geoMatcher.group(2)?.toDoubleOrNull()
            if (isValid(lat, lon)) return LatLng(lat!!, lon!!)
        }

        // 4. Check DMS format
        val dmsMatches = mutableListOf<Pair<Double, Char>>()
        val dmsMatcher = DMS_PATTERN.matcher(trimmed)
        while (dmsMatcher.find()) {
            val deg = dmsMatcher.group(1)?.toDoubleOrNull() ?: continue
            val min = dmsMatcher.group(2)?.toDoubleOrNull() ?: continue
            val sec = dmsMatcher.group(3)?.toDoubleOrNull() ?: continue
            val dir = dmsMatcher.group(4)?.first()?.uppercaseChar() ?: continue

            var dec = deg + (min / 60.0) + (sec / 3600.0)
            if (dir == 'S' || dir == 'W') {
                dec = -dec
            }
            dmsMatches.add(Pair(dec, dir))
        }

        if (dmsMatches.size == 2) {
            val first = dmsMatches[0]
            val second = dmsMatches[1]
            val (lat, lon) = if (first.second in listOf('N', 'S')) {
                Pair(first.first, second.first)
            } else {
                Pair(second.first, first.first)
            }
            if (isValid(lat, lon)) return LatLng(lat, lon)
        }

        // 5. Check Hemisphere Decimal (e.g. "37.7749° N, 122.4194° W" or "N 37.7749, W 122.4194")
        val latMatch = HEMISPHERE_DECIMAL_PATTERN.matcher(trimmed)
        val lonMatch = HEMISPHERE_LON_PATTERN.matcher(trimmed)
        if (latMatch.find() && lonMatch.find()) {
            val latVal = (latMatch.group(2) ?: latMatch.group(3))?.toDoubleOrNull()
            val latHem = (latMatch.group(1) ?: latMatch.group(4))?.first()?.uppercaseChar()

            val lonVal = (lonMatch.group(2) ?: lonMatch.group(3))?.toDoubleOrNull()
            val lonHem = (lonMatch.group(1) ?: lonMatch.group(4))?.first()?.uppercaseChar()

            if (latVal != null && lonVal != null && latHem != null && lonHem != null) {
                val lat = if (latHem == 'S') -latVal else latVal
                val lon = if (lonHem == 'W') -lonVal else lonVal
                if (isValid(lat, lon)) return LatLng(lat, lon)
            }
        }

        // 6. Simple Decimal Pair "lat, lon" or "lat lon"
        val simpleMatcher = SIMPLE_DECIMAL_PATTERN.matcher(trimmed)
        if (simpleMatcher.matches()) {
            val lat = simpleMatcher.group(1)?.toDoubleOrNull()
            val lon = simpleMatcher.group(2)?.toDoubleOrNull()
            if (isValid(lat, lon)) return LatLng(lat!!, lon!!)
        }

        return null
    }

    private fun isValid(lat: Double?, lon: Double?): Boolean {
        if (lat == null || lon == null) return false
        return lat in -90.0..90.0 && lon in -180.0..180.0
    }
}
