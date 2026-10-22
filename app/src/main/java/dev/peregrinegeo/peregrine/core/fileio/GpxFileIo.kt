package dev.peregrinegeo.peregrine.core.fileio

import dev.peregrinegeo.peregrine.core.kinematics.RoutePoint
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.StringReader
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XXE-hardened GPX 1.1 parser and exporter.
 * Preserves elevation and ISO-8601 timestamps.
 */
object GpxFileIo {

    private fun getSecureDocumentBuilder(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        // XXE protections
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.isXIncludeAware = false
        factory.isExpandEntityReferences = false
        return factory
    }

    private val isoFormat: SimpleDateFormat
        get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    fun parse(xmlContent: String, maxPoints: Int = DouglasPeucker.DEFAULT_MAX_POINTS): List<RoutePoint> {
        val factory = getSecureDocumentBuilder()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlContent.byteInputStream())
        doc.documentElement.normalize()

        val points = mutableListOf<RoutePoint>()
        val pointNodes = mutableListOf<Element>()

        // Look for trkpt, rtept, wpt tags
        val tagNames = listOf("trkpt", "rtept", "wpt")
        for (tag in tagNames) {
            val list = doc.getElementsByTagName(tag)
            for (i in 0 until list.length) {
                val node = list.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) {
                    pointNodes.add(node as Element)
                }
            }
        }

        val step = if (pointNodes.size > maxPoints) (pointNodes.size / maxPoints) + 1 else 1

        for (i in pointNodes.indices step step) {
            val el = pointNodes[i]
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: continue
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: continue

            var ele: Double? = null
            val eleNodes = el.getElementsByTagName("ele")
            if (eleNodes.length > 0) {
                ele = eleNodes.item(0).textContent.toDoubleOrNull()
            }

            var timeMs: Long? = null
            val timeNodes = el.getElementsByTagName("time")
            if (timeNodes.length > 0) {
                val timeStr = timeNodes.item(0).textContent
                try {
                    timeMs = isoFormat.parse(timeStr)?.time
                } catch (_: Exception) { }
            }

            points.add(RoutePoint(lat = lat, lon = lon, ele = ele, timeMs = timeMs))
        }

        return points
    }

    fun export(points: List<RoutePoint>, trackName: String = "Peregrine Track"): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="Peregrine" xmlns="http://www.topografix.com/GPX/1/1">""").append("\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(escapeXml(trackName)).append("</name>\n")
        sb.append("    <trkseg>\n")

        for (pt in points) {
            sb.append("""      <trkpt lat="${pt.lat}" lon="${pt.lon}">""").append("\n")
            if (pt.ele != null) {
                sb.append("        <ele>").append(pt.ele).append("</ele>\n")
            }
            if (pt.timeMs != null) {
                sb.append("        <time>").append(isoFormat.format(Date(pt.timeMs))).append("</time>\n")
            }
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
