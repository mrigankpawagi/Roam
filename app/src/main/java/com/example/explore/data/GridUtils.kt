package com.example.explore.data

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
}
