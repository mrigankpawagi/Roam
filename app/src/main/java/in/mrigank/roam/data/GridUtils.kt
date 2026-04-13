package in.mrigank.roam.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

object GridUtils {

    private const val EARTH_RADIUS = 6371000.0

    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return EARTH_RADIUS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun latSpanMeters(area: Area): Double =
        distanceMeters(area.minLat, area.minLng, area.maxLat, area.minLng)

    private fun lngSpanMeters(area: Area): Double =
        distanceMeters(area.minLat, area.minLng, area.minLat, area.maxLng)

    fun numRows(area: Area): Int =
        ceil(latSpanMeters(area) / area.cellSizeMeters).toInt().coerceAtLeast(1)

    fun numCols(area: Area): Int =
        ceil(lngSpanMeters(area) / area.cellSizeMeters).toInt().coerceAtLeast(1)

    fun cellForPoint(area: Area, lat: Double, lng: Double): Pair<Int, Int>? {
        if (lat < area.minLat || lat > area.maxLat || lng < area.minLng || lng > area.maxLng) {
            return null
        }
        val rows = numRows(area)
        val cols = numCols(area)
        val latRange = area.maxLat - area.minLat
        val lngRange = area.maxLng - area.minLng
        if (latRange <= 0 || lngRange <= 0) return null
        val row = ((lat - area.minLat) / latRange * rows).toInt().coerceIn(0, rows - 1)
        val col = ((lng - area.minLng) / lngRange * cols).toInt().coerceIn(0, cols - 1)
        return Pair(row, col)
    }

    fun cellCenterLatLng(area: Area, row: Int, col: Int): Pair<Double, Double> {
        val rows = numRows(area)
        val cols = numCols(area)
        val lat = area.minLat + (row + 0.5) / rows * (area.maxLat - area.minLat)
        val lng = area.minLng + (col + 0.5) / cols * (area.maxLng - area.minLng)
        return Pair(lat, lng)
    }

    fun cellsInRadius(
        area: Area,
        lat: Double,
        lng: Double,
        radiusMeters: Double
    ): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        val rows = numRows(area)
        val cols = numCols(area)
        val latRange = area.maxLat - area.minLat
        val lngRange = area.maxLng - area.minLng
        if (latRange <= 0 || lngRange <= 0) return result

        val metersPerDegLat = EARTH_RADIUS * Math.PI / 180.0
        val metersPerDegLng = metersPerDegLat * cos(Math.toRadians(lat))

        val latDelta = radiusMeters / metersPerDegLat
        val lngDelta = if (metersPerDegLng > 0) radiusMeters / metersPerDegLng else 360.0

        val startRow = ((lat - latDelta - area.minLat) / latRange * rows)
            .toInt().coerceIn(0, rows - 1)
        val endRow = ((lat + latDelta - area.minLat) / latRange * rows)
            .toInt().coerceIn(0, rows - 1)
        val startCol = ((lng - lngDelta - area.minLng) / lngRange * cols)
            .toInt().coerceIn(0, cols - 1)
        val endCol = ((lng + lngDelta - area.minLng) / lngRange * cols)
            .toInt().coerceIn(0, cols - 1)

        for (r in startRow..endRow) {
            for (c in startCol..endCol) {
                val (centerLat, centerLng) = cellCenterLatLng(area, r, c)
                if (distanceMeters(lat, lng, centerLat, centerLng) <= radiusMeters) {
                    result.add(Pair(r, c))
                }
            }
        }
        return result
    }

    /**
     * Ray-casting point-in-polygon test.
     * [polygon] is a list of (lat, lng) pairs forming a closed ring (first == last is optional).
     */
    fun pointInPolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val (iLat, iLng) = polygon[i]
            val (jLat, jLng) = polygon[j]
            if ((iLng > lng) != (jLng > lng) &&
                lat < (jLat - iLat) * (lng - iLng) / (jLng - iLng) + iLat
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /**
     * Parse polygonsJson string into a list of polygon rings.
     * Each ring is a list of (lat, lng) pairs.
     */
    fun parsePolygons(json: String): List<List<Pair<Double, Double>>> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val ring = arr.getJSONArray(i)
                (0 until ring.length()).map { j ->
                    val pt = ring.getJSONObject(j)
                    Pair(pt.getDouble("lat"), pt.getDouble("lng"))
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize a list of polygon rings to a JSON string.
     */
    fun serializePolygons(polygons: List<List<Pair<Double, Double>>>): String {
        val arr = JSONArray()
        for (ring in polygons) {
            val rArr = JSONArray()
            for ((lat, lng) in ring) {
                rArr.put(JSONObject().apply {
                    put("lat", lat)
                    put("lng", lng)
                })
            }
            arr.put(rArr)
        }
        return arr.toString()
    }

    /**
     * Compute a bounding box that tightly encloses all given polygons.
     * Returns (minLat, maxLat, minLng, maxLng) or null if the polygon list is empty.
     */
    fun boundingBox(polygons: List<List<Pair<Double, Double>>>): Quadruple? {
        val allPoints = polygons.flatten()
        if (allPoints.isEmpty()) return null
        return Quadruple(
            minLat = allPoints.minOf { it.first },
            maxLat = allPoints.maxOf { it.first },
            minLng = allPoints.minOf { it.second },
            maxLng = allPoints.maxOf { it.second }
        )
    }

    data class Quadruple(val minLat: Double, val maxLat: Double, val minLng: Double, val maxLng: Double)
}
