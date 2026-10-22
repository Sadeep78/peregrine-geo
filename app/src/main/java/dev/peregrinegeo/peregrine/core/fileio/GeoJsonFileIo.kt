package dev.peregrinegeo.peregrine.core.fileio

import dev.peregrinegeo.peregrine.core.kinematics.RoutePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

/**
 * Lightweight pure Kotlin GeoJSON parser and exporter.
 * Preserves elevation coordinates and coordinateProperties.times.
 */
object GeoJsonFileIo {

    private val isoFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun parse(jsonContent: String, maxPoints: Int = DouglasPeucker.DEFAULT_MAX_POINTS): List<RoutePoint> {
        val points = mutableListOf<RoutePoint>()

        // 1. Extract times if present: "times": [ "...", "..." ]
        val times = mutableListOf<Long>()
        val timesMatch = Pattern.compile(""""times"\s*:\s*\[([^\]]+)\]""").matcher(jsonContent)
        if (timesMatch.find()) {
            val timesRaw = timesMatch.group(1)
            val timeTokens = timesRaw.split(",")
            for (token in timeTokens) {
                val cleaned = token.trim().replace("\"", "")
                val parsed = isoFormat.parse(cleaned)?.time ?: cleaned.toLongOrNull()
                if (parsed != null) times.add(parsed)
            }
        }

        // 2. Extract coordinates: [lon, lat, ele?]
        val coordRegex = Pattern.compile("""\[\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)(?:\s*,\s*(-?\d+(?:\.\d+)?))?\s*\]""")
        val matcher = coordRegex.matcher(jsonContent)

        var idx = 0
        while (matcher.find()) {
            val lon = matcher.group(1)?.toDoubleOrNull() ?: continue
            val lat = matcher.group(2)?.toDoubleOrNull() ?: continue
            val ele = matcher.group(3)?.toDoubleOrNull()

            val timeMs = if (idx < times.size) times[idx] else null
            points.add(RoutePoint(lat = lat, lon = lon, ele = ele, timeMs = timeMs))
            idx++

            if (points.size >= maxPoints) break
        }

        return points
    }

    fun export(points: List<RoutePoint>, name: String = "Peregrine Track"): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("""  "type": "FeatureCollection",""").append("\n")
        sb.append("""  "features": [""").append("\n")
        sb.append("    {\n")
        sb.append("""      "type": "Feature",""").append("\n")
        sb.append("""      "properties": {""").append("\n")
        sb.append("""        "name": "$name"""")

        val hasTimes = points.any { it.timeMs != null }
        if (hasTimes) {
            sb.append(",\n")
            sb.append("""        "coordinateProperties": {""").append("\n")
            sb.append("""          "times": [""")
            val timeStrs = points.map { pt ->
                if (pt.timeMs != null) "\"${isoFormat.format(Date(pt.timeMs))}\"" else "null"
            }
            sb.append(timeStrs.joinToString(", "))
            sb.append("]\n        }\n")
        } else {
            sb.append("\n")
        }

        sb.append("      },\n")
        sb.append("""      "geometry": {""").append("\n")
        sb.append("""        "type": "LineString",""").append("\n")
        sb.append("""        "coordinates": [""").append("\n")

        for (i in points.indices) {
            val pt = points[i]
            sb.append("          [${pt.lon}, ${pt.lat}")
            if (pt.ele != null) {
                sb.append(", ${pt.ele}")
            }
            sb.append("]")
            if (i < points.size - 1) sb.append(",")
            sb.append("\n")
        }

        sb.append("        ]\n")
        sb.append("      }\n")
        sb.append("    }\n")
        sb.append("  ]\n")
        sb.append("}\n")
        return sb.toString()
    }
}
