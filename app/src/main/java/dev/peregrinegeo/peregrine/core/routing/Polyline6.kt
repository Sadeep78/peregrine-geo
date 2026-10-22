package dev.peregrinegeo.peregrine.core.routing

import dev.peregrinegeo.peregrine.core.geo.LatLng
import kotlin.math.roundToLong

/**
 * Encodes and decodes Google Polyline format with 1e6 (Polyline6) precision.
 * Used by OSRM (geometries=polyline6).
 */
object Polyline6 {

    private const val PRECISION = 1e6

    fun decode(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat / PRECISION, lng / PRECISION)
            poly.add(p)
        }

        return poly
    }

    fun encode(points: List<LatLng>): String {
        val sb = StringBuilder()
        var lastLat: Long = 0
        var lastLng: Long = 0

        for (point in points) {
            val lat = (point.latitude * PRECISION).roundToLong()
            val lng = (point.longitude * PRECISION).roundToLong()

            encodeValue(lat - lastLat, sb)
            encodeValue(lng - lastLng, sb)

            lastLat = lat
            lastLng = lng
        }

        return sb.toString()
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }
}
