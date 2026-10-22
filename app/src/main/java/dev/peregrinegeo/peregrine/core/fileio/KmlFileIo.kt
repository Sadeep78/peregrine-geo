package dev.peregrinegeo.peregrine.core.fileio

import dev.peregrinegeo.peregrine.core.kinematics.RoutePoint
import org.w3c.dom.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/**
 * XXE-hardened KML 2.2 parser and exporter.
 * Supports standard LineString coordinates and gx:Track extensions.
 */
object KmlFileIo {

    private fun getSecureDocumentBuilder(): DocumentBuilderFactory {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
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

    fun parse(kmlContent: String, maxPoints: Int = DouglasPeucker.DEFAULT_MAX_POINTS): List<RoutePoint> {
        val factory = getSecureDocumentBuilder()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(kmlContent.byteInputStream())
        doc.documentElement.normalize()

        val points = mutableListOf<RoutePoint>()

        // 1. Check for gx:Track (<when> and <gx:coord>)
        val whenList = doc.getElementsByTagName("when")
        val coordList = doc.getElementsByTagName("gx:coord")

        if (coordList.length > 0) {
            val count = coordList.length
            val step = if (count > maxPoints) (count / maxPoints) + 1 else 1

            for (i in 0 until count step step) {
                val coordText = coordList.item(i).textContent.trim()
                val parts = coordText.split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull() ?: continue
                    val lat = parts[1].toDoubleOrNull() ?: continue
                    val ele = if (parts.size >= 3) parts[2].toDoubleOrNull() else null

                    var timeMs: Long? = null
                    if (i < whenList.length) {
                        try {
                            timeMs = isoFormat.parse(whenList.item(i).textContent.trim())?.time
                        } catch (_: Exception) { }
                    }

                    points.add(RoutePoint(lat = lat, lon = lon, ele = ele, timeMs = timeMs))
                }
            }
            if (points.isNotEmpty()) return points
        }

        // 2. Check for LineString <coordinates>
        val coordNodes = doc.getElementsByTagName("coordinates")
        for (cIdx in 0 until coordNodes.length) {
            val text = coordNodes.item(cIdx).textContent.trim()
            val tupleTokens = text.split("\\s+".toRegex())

            val step = if (tupleTokens.size > maxPoints) (tupleTokens.size / maxPoints) + 1 else 1

            for (i in tupleTokens.indices step step) {
                val token = tupleTokens[i].trim()
                if (token.isEmpty()) continue
                val parts = token.split(",")
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull() ?: continue
                    val lat = parts[1].toDoubleOrNull() ?: continue
                    val ele = if (parts.size >= 3) parts[2].toDoubleOrNull() else null
                    points.add(RoutePoint(lat = lat, lon = lon, ele = ele))
                }
            }
        }

        return points
    }

    fun export(points: List<RoutePoint>, name: String = "Peregrine Route"): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">""").append("\n")
        sb.append("  <Document>\n")
        sb.append("    <name>").append(escapeXml(name)).append("</name>\n")
        sb.append("    <Placemark>\n")
        sb.append("      <name>").append(escapeXml(name)).append("</name>\n")
        sb.append("      <LineString>\n")
        sb.append("        <tessellate>1</tessellate>\n")
        sb.append("        <coordinates>\n")

        for (pt in points) {
            val ele = pt.ele ?: 0.0
            sb.append("          ${pt.lon},${pt.lat},$ele\n")
        }

        sb.append("        </coordinates>\n")
        sb.append("      </LineString>\n")
        sb.append("    </Placemark>\n")
        sb.append("  </Document>\n")
        sb.append("</kml>\n")
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
